"""Build the Raasta demo corridor data file from real public data.

Fetches:
  * PMPML static GTFS (community-built feed) for bus routes, stops and timetables
  * OpenStreetMap metro station locations (Nominatim lookup by OSM id)
  * Walking and driving routes (OSRM servers on routing.openstreetmap.de)

Fares and service facts that are only published as charts or notices are kept
in FACTS below, each with its source. Output: app/app/src/main/assets/corridor.json

Usage (standard library only):
    python -I tools/corridor/build_corridor.py
"""

import csv
import io
import json
import math
import statistics
import time
import urllib.parse
import urllib.request
import zipfile
from collections import defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "app" / "src" / "main" / "assets" / "corridor.json"
CACHE = Path(__file__).resolve().parent / ".cache"
USER_AGENT = "RaastaDemoDataBuild/0.1"
RETRIEVED = date.today().isoformat()

GTFS_URL = "https://rt-bucket.blrtransit.com/rt/control/static/gtfs.zip"
OSRM = "https://routing.openstreetmap.de/routed-{profile}/route/v1/driving/{a};{b}?overview=false"
NOMINATIM_LOOKUP = "https://nominatim.openstreetmap.org/lookup"

# Places the demo offers. Coordinates are PMPML stop locations from the GTFS feed.
ORIGINS = [
    {"id": "wtc", "name": "World Trade Center, Kharadi", "lat": 18.5529, "lon": 73.9478},
    {"id": "eon", "name": "EON IT Park Phase 2, Kharadi", "lat": 18.5525, "lon": 73.955902},
]
DESTINATIONS = [
    {"id": "vanaj", "name": "Vanaj Corner, Kothrud", "lat": 18.5075, "lon": 73.807503},
    {"id": "anand", "name": "Anand Nagar, Kothrud", "lat": 18.509708, "lon": 73.814425},
    {"id": "deccan", "name": "Deccan Gymkhana", "lat": 18.516678, "lon": 73.843003},
]

# Aqua Line from Ramwadi to Vanaz. Names follow the official fare chart;
# osm is the OpenStreetMap station object (name there may be an older one).
AQUA_LINE = [
    ("Ramwadi", "N11699340657"),
    ("Kalyani Nagar", "N11699340655"),
    ("Yerwada", "N11699340652"),
    ("Bund Garden", "N11699340649"),
    ("Ruby Hall Clinic", "N7944584984"),
    ("Pune Railway Station", "W1494691943"),
    ("R.T.O.", "W1532296305"),  # OSM: Mangalwar Peth
    ("District Court", "N7944597685"),  # OSM: District Court (Aqua Line)
    ("PMC", "N7944584980"),  # OSM: PMC Bhavan
    ("Chhatrapati Sambhaji Udyan", "N11097527718"),
    ("Deccan Gymkhana", "N11097527725"),
    ("Garware College", "N9599243857"),
    ("S.N.D.T. College", "N9599243856"),
    ("Paud Phata", "N9599243855"),
    ("Anand Nagar", "N9599243854"),  # OSM: Anandnagar
    ("Vanaz", "N9599243853"),
]

TRANSFER_STATION = "Ramwadi"
TRANSFER_BUS_STOP_NAME = "Ramwadi Metro Station"
MAX_WALK_TO_STOP_M = 900

SOURCES = [
    {
        "id": "metro_fare",
        "title": "Pune Metro official fare chart",
        "url": "https://www.punemetrorail.org/fare_chart.aspx",
        "retrieved": "2026-10-23",
        "note": "Station-to-station fares transcribed from the published chart image (undated).",
    },
    {
        "id": "pmpml_fare",
        "title": "PMPML fare revision from 1 June 2025 (Punekar News)",
        "url": "https://www.punekarnews.in/pune-pmpml-fare-hike-comes-into-effect-from-today-daily-and-monthly-passes-get-costlier/",
        "retrieved": "2026-10-23",
        "note": "First four 5 km slabs as reported. PMPML's official Marathi press note was not verified.",
    },
    {
        "id": "auto_fare",
        "title": "Pune RTA auto-rickshaw fare revision from 1 September 2026 (Free Press Journal)",
        "url": "https://www.freepressjournal.in/pune/pune-rta-approves-autorickshaw-fare-hike-from-september-1",
        "retrieved": "2026-10-23",
        "note": "Rs 30 for the first 1.5 km, Rs 20 per km after; 25% night surcharge 12 AM to 5 AM within Pune and PCMC.",
    },
    {
        "id": "metro_service",
        "title": "Pune Metro Aqua Line timings (Housing.com, secondary source)",
        "url": "https://housing.com/news/pune-metro-aqua-line-line-2-route-map-timings-fare/",
        "retrieved": "2026-10-23",
        "note": "About 6 AM to 10-11 PM; every 7.5 min at peak and 10 min off-peak. Not an official timetable.",
    },
    {
        "id": "metro_length",
        "title": "Pune Metro Aqua Line length (Wikipedia)",
        "url": "https://en.wikipedia.org/wiki/Pune_Metro",
        "retrieved": "2026-10-23",
        "note": "Vanaz to Ramwadi is 14.66 km; used to scale straight-line station distances.",
    },
    {
        "id": "pmpml_gtfs",
        "title": "PMPML static GTFS (community-built, blrtransit.com)",
        "url": GTFS_URL,
        "retrieved": RETRIEVED,
        "note": "Unofficial and not exhaustive; very few trips are listed after 4 PM.",
    },
    {
        "id": "osm",
        "title": "Metro station locations from OpenStreetMap (Nominatim)",
        "url": "https://www.openstreetmap.org/copyright",
        "retrieved": RETRIEVED,
        "note": "Map data (c) OpenStreetMap contributors, ODbL.",
    },
    {
        "id": "osrm",
        "title": "Walking and driving routes from OSRM (routing.openstreetmap.de)",
        "url": "https://routing.openstreetmap.de/about.html",
        "retrieved": RETRIEVED,
        "note": "Computed on OpenStreetMap data. Auto distance uses the car profile.",
    },
]

FACTS = {
    # Official chart, row "Ramwadi" (Rs).
    "metroFareFromRamwadi": {
        "Kalyani Nagar": 10, "Yerwada": 15, "Bund Garden": 20, "Ruby Hall Clinic": 20,
        "Pune Railway Station": 25, "R.T.O.": 25, "District Court": 25, "PMC": 25,
        "Chhatrapati Sambhaji Udyan": 25, "Deccan Gymkhana": 25, "Garware College": 25,
        "S.N.D.T. College": 25, "Paud Phata": 30, "Anand Nagar": 30, "Vanaz": 30,
    },
    "pmpmlFareSlabs": [
        {"upToKm": 5, "fare": 10},
        {"upToKm": 10, "fare": 20},
        {"upToKm": 15, "fare": 30},
        {"upToKm": 20, "fare": 40},
    ],
    "autoFare": {
        "baseFare": 30, "baseKm": 1.5, "perKm": 20,
        "nightSurchargePercent": 25, "nightStart": "00:00", "nightEnd": "05:00",
    },
    "metroService": {
        "firstTrain": "06:00",
        "lastTrainEarliest": "22:00",  # sources disagree: 10 PM or 11 PM
        "lastTrainLatest": "23:00",
        "headwayPeakMin": 7.5,
        "headwayOffPeakMin": 10,
        "peakWindows": [["08:00", "11:00"], ["16:00", "20:00"]],
    },
    "aquaLineLengthKm": 14.66,
    # Assumptions, shown in the app as estimates.
    "assumptions": {
        "metroAvgSpeedKmh": 30,
        "stationEntryMin": 3,
        "autoAvgSpeedKmh": 20,
        "autoPickupWaitMin": 5,
    },
}


def http_get(url, params=None, timeout=60):
    if params:
        url = url + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def haversine_m(lat1, lon1, lat2, lon2):
    r = 6371000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def osrm(profile, a, b):
    """Returns (metres, minutes) for a walk ("foot") or drive ("car")."""
    url = OSRM.format(profile=profile, a=f"{a['lon']},{a['lat']}", b=f"{b['lon']},{b['lat']}")
    data = json.loads(http_get(url))
    if data.get("code") != "Ok":
        raise RuntimeError(f"OSRM {profile} failed: {data}")
    route = data["routes"][0]
    time.sleep(0.5)  # be gentle with the shared server
    return round(route["distance"]), round(route["duration"] / 60, 1)


def to_minutes(hhmmss):
    h, m, s = (int(x) for x in hhmmss.split(":"))
    return h * 60 + m + s / 60


def load_gtfs():
    CACHE.mkdir(exist_ok=True)
    path = CACHE / "pmpml_gtfs.zip"
    if not path.exists():
        path.write_bytes(http_get(GTFS_URL, timeout=180))
    z = zipfile.ZipFile(path)

    def rows(name):
        return csv.DictReader(io.TextIOWrapper(z.open(name), encoding="utf-8-sig"))

    feed = next(rows("feed_info.txt"))
    stops = {s["stop_id"]: s for s in rows("stops.txt")}
    routes = {r["route_id"]: r for r in rows("routes.txt")}
    trips = {t["trip_id"]: t for t in rows("trips.txt")}
    return z, rows, feed, stops, routes, trips


def stop_point(stop):
    return {"name": stop["stop_name"], "lat": float(stop["stop_lat"]), "lon": float(stop["stop_lon"])}


def bus_options(origin, rows, stops, routes, trips, transfer_ids):
    """Bus routes that serve a stop near the origin and later Ramwadi Metro Station."""
    near = {
        sid for sid, s in stops.items()
        if haversine_m(origin["lat"], origin["lon"], float(s["stop_lat"]), float(s["stop_lon"])) <= MAX_WALK_TO_STOP_M
    }
    seq = defaultdict(list)
    for r in rows("stop_times.txt"):
        if r["stop_id"] in near or r["stop_id"] in transfer_ids:
            seq[r["trip_id"]].append((
                int(r["stop_sequence"]), r["stop_id"], r["departure_time"], r["arrival_time"],
                float(r["shape_dist_traveled"] or 0),
            ))

    by_route = defaultdict(list)
    for trip_id, calls in seq.items():
        calls.sort()
        boards = [c for c in calls if c[1] in near]
        if not boards:
            continue
        # Board at the stop on this trip closest to the origin.
        board = min(boards, key=lambda c: haversine_m(
            origin["lat"], origin["lon"], float(stops[c[1]]["stop_lat"]), float(stops[c[1]]["stop_lon"])))
        alights = [c for c in calls if c[1] in transfer_ids and c[0] > board[0]]
        if alights:
            by_route[trips[trip_id]["route_id"]].append((board, alights[0]))

    options = []
    for route_id, pairs in by_route.items():
        if len(pairs) < 3:  # too few listed trips to be a usable option
            continue
        board_stop = stops[pairs[0][0][1]]
        departures = sorted({b[2][:5] for b, _ in pairs})
        ride_min = statistics.median(to_minutes(a[3]) - to_minutes(b[2]) for b, a in pairs)
        ride_km = statistics.median((a[4] - b[4]) / 1000 for b, a in pairs)
        dep_minutes = sorted(to_minutes(b[2]) for b, _ in pairs)
        gaps = [y - x for x, y in zip(dep_minutes, dep_minutes[1:]) if y > x]
        route = routes[route_id]
        options.append({
            "type": "bus",
            "route": route["route_short_name"],
            "routeName": route["route_long_name"],
            "boardStop": stop_point(board_stop),
            "alightStop": stop_point(stops[pairs[0][1][1]]),
            "rideKm": round(ride_km, 2),
            "rideMin": round(ride_min, 1),
            "departures": departures,
            "medianHeadwayMin": round(statistics.median(gaps), 1) if gaps else None,
        })
    return options


def build_recovery_points(origins, transfer):
    """Bus stops where a rider can be stranded, with walks to the other stops and auto rides out."""
    boards = {}
    for origin in origins:
        for option in origin["accessToMetro"]:
            if option["type"] == "bus":
                stop = option["boardStop"]
                boards.setdefault(stop["name"], {**stop, "routes": set()})["routes"].add(option["route"])

    points = []
    for name, stop in boards.items():
        walks = []
        for other_name, other in boards.items():
            if other_name == name:
                walks.append({"to": other_name, "m": 0, "min": 0.0})
            else:
                m, minutes = osrm("foot", stop, other)
                walks.append({"to": other_name, "m": m, "min": minutes})
        auto_m, auto_min = osrm("car", stop, transfer)
        to_destinations = []
        for dest in DESTINATIONS:
            m, minutes = osrm("car", stop, dest)
            to_destinations.append({"destination": dest["id"], "km": round(m / 1000, 2), "freeFlowMin": minutes})
        points.append({
            "id": name.lower().replace(".", "").replace(" ", "_"),
            "name": name,
            "lat": stop["lat"],
            "lon": stop["lon"],
            "routes": sorted(stop["routes"]),
            "walksToStops": walks,
            "autoToTransfer": {"km": round(auto_m / 1000, 2), "freeFlowMin": auto_min},
            "autoToDestinations": to_destinations,
        })
    return points


def main():
    z, rows, feed, stops, routes, trips = load_gtfs()
    gtfs_source = next(s for s in SOURCES if s["id"] == "pmpml_gtfs")
    gtfs_source["note"] += f" Feed valid {feed['feed_start_date']} to {feed['feed_end_date']}."

    looked_up = json.loads(http_get(NOMINATIM_LOOKUP, {
        "osm_ids": ",".join(osm for _, osm in AQUA_LINE), "format": "jsonv2",
    }))
    by_osm = {f"{p['osm_type'][0].upper()}{p['osm_id']}": p for p in looked_up}
    stations = []
    km = 0.0
    for name, osm_id in AQUA_LINE:
        place = by_osm[osm_id]
        lat, lon = float(place["lat"]), float(place["lon"])
        if stations:
            prev = stations[-1]
            km += haversine_m(prev["lat"], prev["lon"], lat, lon) / 1000
        stations.append({
            "name": name, "lat": lat, "lon": lon, "osmName": place["name"],
            "straightKmFromRamwadi": km,
            "fareFromRamwadi": FACTS["metroFareFromRamwadi"].get(name, 0),
        })
    # Scale straight-line distances so the whole line matches its published length.
    scale = FACTS["aquaLineLengthKm"] / stations[-1]["straightKmFromRamwadi"]
    for s in stations:
        s["kmFromRamwadi"] = round(s.pop("straightKmFromRamwadi") * scale, 2)
    transfer = next(s for s in stations if s["name"] == TRANSFER_STATION)
    transfer_ids = {sid for sid, s in stops.items() if s["stop_name"] == TRANSFER_BUS_STOP_NAME}

    origins = []
    for origin in ORIGINS:
        access = []
        for option in bus_options(origin, rows, stops, routes, trips, transfer_ids):
            option["walkToStopM"], option["walkToStopMin"] = osrm("foot", origin, option["boardStop"])
            option["walkToStationM"], option["walkToStationMin"] = osrm("foot", option["alightStop"], transfer)
            access.append(option)
        auto_m, auto_min = osrm("car", origin, transfer)
        access.append({"type": "auto", "km": round(auto_m / 1000, 2), "freeFlowMin": auto_min})
        origins.append({**origin, "accessToMetro": access})

    destinations = []
    for dest in DESTINATIONS:
        station = min(stations, key=lambda s: haversine_m(dest["lat"], dest["lon"], s["lat"], s["lon"]))
        walk_m, walk_min = osrm("foot", station, dest)
        destinations.append({**dest, "metroStation": station["name"], "walkFromStationM": walk_m,
                             "walkFromStationMin": walk_min})

    auto_direct = []
    for origin in ORIGINS:
        for dest in DESTINATIONS:
            m, minutes = osrm("car", origin, dest)
            auto_direct.append({"origin": origin["id"], "destination": dest["id"],
                                "km": round(m / 1000, 2), "freeFlowMin": minutes})

    recovery_points = build_recovery_points(origins, transfer)

    data = {
        "corridor": "Kharadi to Kothrud / Deccan via Ramwadi Metro (Aqua Line)",
        "generatedOn": RETRIEVED,
        "sources": SOURCES,
        "fares": {k: FACTS[k] for k in ("pmpmlFareSlabs", "autoFare")},
        "metro": {"line": "Aqua Line", "transferStation": TRANSFER_STATION, "stations": stations,
                  "service": FACTS["metroService"]},
        "assumptions": FACTS["assumptions"],
        "origins": origins,
        "destinations": destinations,
        "autoDirect": auto_direct,
        "recoveryPoints": recovery_points,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
