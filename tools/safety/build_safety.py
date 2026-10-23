"""Build the street safety data for Raasta's shortest-vs-safety routes.

Fetches from OpenStreetMap (Overpass API) for the demo corridor:
  * the street network (walkable and drivable ways), split into a routing graph
  * places that bring people to a street (shops, food, services, transit stops)
    with their opening hours, so "how busy" can change with the time of day
  * help points (police, hospital, fuel, pharmacy) that stay open late, transit stops
    and publicly mapped surveillance cameras, counted per street for the night score

Writes:
  * app/app/src/main/assets/street_graph.bin  routing graph + people-activity data
  * app/app/src/main/assets/crime_zones.json  DEMO crime layer (synthetic, see below)

Crime: no open street-level crime data exists for Pune (NCRB publishes city totals
only). The crime file this script writes is SYNTHETIC, generated from a fixed seed,
flagged "demo": true and labelled as such in the app. Replace it with real data in
the same format when it becomes available (see README.md); --keep-crime leaves an
existing file alone.

Usage (standard library only):
    python -I tools/safety/build_safety.py [--keep-crime]
"""

import json
import math
import random
import re
import struct
import sys
import time
import urllib.parse
import urllib.request
from collections import defaultdict, deque
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "app" / "src" / "main" / "assets"
GRAPH_OUT = ASSETS / "street_graph.bin"
CRIME_OUT = ASSETS / "crime_zones.json"
CACHE = Path(__file__).resolve().parent / ".cache"
USER_AGENT = "RaastaDemoDataBuild/0.1"
RETRIEVED = date.today().isoformat()
OVERPASS = "https://overpass-api.de/api/interpreter"

# Kharadi to Kothrud, with a margin around every place in corridor.json.
SOUTH, WEST, NORTH, EAST = 18.495, 73.795, 18.568, 73.966

# Road classes, in the order the app's RoadClass enum uses.
CLASSES = [
    "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
    "living_street", "service", "pedestrian", "footway",
]
CLASS_OF = {
    "trunk": 0, "trunk_link": 0, "primary": 1, "primary_link": 1,
    "secondary": 2, "secondary_link": 2, "tertiary": 3, "tertiary_link": 3,
    "unclassified": 4, "residential": 5, "living_street": 6, "service": 7,
    "pedestrian": 8, "footway": 9, "path": 9, "steps": 9, "track": 9, "cycleway": 9,
}
CAR_CLASSES = {0, 1, 2, 3, 4, 5, 6, 7}
FOOT = 1
CAR = 2
ONEWAY = 4  # car may only travel a -> b
SKIP_SERVICE = {"parking_aisle", "driveway", "drive-through"}

# A place counts towards a street when it is this close to it.
POI_NEAR_M = 40
CAMERA_NEAR_M = 50
FULL_DAY = (1 << 24) - 1

# Typical hours (24 h clock) when OSM has no usable opening_hours tag.
# Assumptions for Pune, not measured; listed in the app's data notes.
DEFAULT_HOURS = {
    "shop": (10, 22),
    "food": (8, 23),
    "bank": (10, 17),
    "pharmacy": (9, 23),
    "clinic": (9, 21),
    "cinema": (10, 24),
    "marketplace": (7, 21),
    "hospital": (0, 24),
    "fuel": (0, 24),
    "police": (0, 24),
    "atm": (0, 24),
    "transit": (6, 23),
}
AMENITY_KIND = {
    "restaurant": "food", "cafe": "food", "fast_food": "food", "food_court": "food",
    "ice_cream": "food", "bar": "food", "pub": "food",
    "bank": "bank", "pharmacy": "pharmacy", "clinic": "clinic", "doctors": "clinic",
    "cinema": "cinema", "marketplace": "marketplace",
    "hospital": "hospital", "fuel": "fuel", "police": "police", "atm": "atm",
    "bus_station": "transit",
}
# Order of the kind byte the app's PlaceKind enum reads.
KINDS = ["shop", "food", "bank", "pharmacy", "clinic", "cinema", "marketplace",
         "hospital", "fuel", "police", "atm", "transit"]
# Places a person could walk into for help late at night.
HELP_KINDS = {"pharmacy", "hospital", "fuel", "police"}

# Demo crime layer. Fixed seed so every build gives the same zones.
CRIME_SEED = 20261024
CRIME_ZONES = 30


def overpass(name, query):
    CACHE.mkdir(exist_ok=True)
    cached = CACHE / f"{name}.json"
    if cached.exists():
        return json.loads(cached.read_text(encoding="utf-8"))
    body = urllib.parse.urlencode({"data": query}).encode()
    for attempt in range(4):
        try:
            request = urllib.request.Request(OVERPASS, data=body, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=300) as response:
                text = response.read().decode("utf-8")
            data = json.loads(text)
            cached.write_text(text, encoding="utf-8")
            return data
        except Exception as error:  # Overpass is busy at times; back off and retry.
            print(f"  overpass {name} failed ({error}); retrying", file=sys.stderr)
            time.sleep(20 * (attempt + 1))
    raise SystemExit(f"Overpass query {name} failed")


def haversine(a, b):
    r = 6371000.0
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * r * math.atan2(math.sqrt(h), math.sqrt(1 - h))


def access_flags(tags):
    highway = tags.get("highway")
    cls = CLASS_OF.get(highway)
    if cls is None:
        return None
    if tags.get("access") in ("private", "no") or tags.get("area") == "yes":
        return None
    if highway == "service" and tags.get("service") in SKIP_SERVICE:
        return None
    flags = 0
    if tags.get("foot") != "no":
        flags |= FOOT
    if cls in CAR_CLASSES and tags.get("motor_vehicle") != "no" and tags.get("motorcar") != "no":
        flags |= CAR
    if flags == 0:
        return None
    return cls, flags


def oneway_direction(tags):
    """1 = forward only, -1 = backward only, 0 = both ways (for cars)."""
    value = tags.get("oneway")
    if value in ("yes", "1", "true") or tags.get("junction") == "roundabout":
        return 1
    if value == "-1":
        return -1
    return 0


def build_graph(data):
    coords = {e["id"]: (e["lat"], e["lon"]) for e in data["elements"] if e["type"] == "node"}
    ways = []
    use = defaultdict(int)
    for e in data["elements"]:
        if e["type"] != "way":
            continue
        tags = e.get("tags", {})
        flagged = access_flags(tags)
        if flagged is None:
            continue
        nodes = [n for n in e["nodes"] if n in coords]
        if len(nodes) < 2:
            continue
        cls, flags = flagged
        direction = oneway_direction(tags)
        if direction == -1:
            nodes.reverse()
        if direction != 0 and flags & CAR:
            flags |= ONEWAY
        ways.append((nodes, cls, flags))
        for i, n in enumerate(nodes):
            # Ends count twice so they always become graph nodes.
            use[n] += 2 if i in (0, len(nodes) - 1) else 1

    edges = []  # (a_osm, b_osm, [intermediate osm ids], cls, flags)
    for nodes, cls, flags in ways:
        start = 0
        for i in range(1, len(nodes)):
            if use[nodes[i]] >= 2 or i == len(nodes) - 1:
                if nodes[start] != nodes[i]:
                    edges.append((nodes[start], nodes[i], nodes[start + 1:i], cls, flags))
                start = i

    # Keep the largest connected piece so a place never snaps onto an island.
    adjacency = defaultdict(list)
    for a, b, *_ in edges:
        adjacency[a].append(b)
        adjacency[b].append(a)
    seen, best = set(), set()
    for start in adjacency:
        if start in seen:
            continue
        part, queue = {start}, deque([start])
        seen.add(start)
        while queue:
            for nxt in adjacency[queue.popleft()]:
                if nxt not in seen:
                    seen.add(nxt)
                    part.add(nxt)
                    queue.append(nxt)
        if len(part) > len(best):
            best = part
    edges = [e for e in edges if e[0] in best]

    index = {}
    for a, b, *_ in edges:
        for n in (a, b):
            if n not in index:
                index[n] = len(index)
    nodes_out = [None] * len(index)
    for osm_id, i in index.items():
        nodes_out[i] = coords[osm_id]
    edges_out = []
    for a, b, middle, cls, flags in edges:
        geometry = [coords[n] for n in middle]
        points = [coords[a]] + geometry + [coords[b]]
        length = sum(haversine(p, q) for p, q in zip(points, points[1:]))
        edges_out.append({"a": index[a], "b": index[b], "len": length, "cls": cls, "flags": flags, "geom": geometry})
    return nodes_out, edges_out


def parse_hours(text):
    """Hour mask from simple OSM opening_hours values; None when it is not understood."""
    if not text:
        return None
    text = text.strip()
    if text == "24/7":
        return (1 << 24) - 1
    ranges = re.findall(r"(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})", text)
    if not ranges:
        return None
    mask = 0
    for h1, m1, h2, m2 in ranges:
        start = int(h1) + int(m1) / 60
        end = int(h2) + int(m2) / 60
        if end <= start:
            end += 24
        for hour in range(24):
            for t in (hour + 0.5, hour + 24.5):
                if start <= t < end:
                    mask |= 1 << hour
    return mask or None


def hours_mask(start, end):
    return sum(1 << h for h in range(24) if start <= h < end)


def poi_kind(tags):
    if "shop" in tags:
        return "shop"
    if tags.get("amenity") in AMENITY_KIND:
        return AMENITY_KIND[tags["amenity"]]
    if tags.get("highway") == "bus_stop" or tags.get("railway") == "station" or tags.get("public_transport") == "station":
        return "transit"
    return None


def build_cameras(data):
    return [(e["lat"], e["lon"]) for e in data["elements"] if e["type"] == "node" and "lat" in e]


def build_pois(data):
    pois = []
    for e in data["elements"]:
        tags = e.get("tags", {})
        kind = poi_kind(tags)
        if kind is None:
            continue
        lat = e.get("lat", e.get("center", {}).get("lat"))
        lon = e.get("lon", e.get("center", {}).get("lon"))
        if lat is None:
            continue
        mask = parse_hours(tags.get("opening_hours"))
        tagged = mask is not None
        if mask is None:
            mask = hours_mask(*DEFAULT_HOURS[kind])
        pois.append({"lat": lat, "lon": lon, "mask": mask, "kind": kind, "tagged": tagged})
    return pois


def street_grid(nodes, edges):
    cell = 0.0005  # about 55 m
    grid = defaultdict(list)
    for i, e in enumerate(edges):
        points = [nodes[e["a"]]] + e["geom"] + [nodes[e["b"]]]
        for p, q in zip(points, points[1:]):
            for cx in range(int(min(p[1], q[1]) / cell) - 1, int(max(p[1], q[1]) / cell) + 2):
                for cy in range(int(min(p[0], q[0]) / cell) - 1, int(max(p[0], q[0]) / cell) + 2):
                    grid[(cx, cy)].append((i, p, q))
    return grid, cell


def streets_near(grid, cell, lat, lon, within_m):
    """Indices of the streets passing within [within_m] metres of a point."""
    k = 111320.0
    near = set()
    for i, p, q in grid.get((int(lon / cell), int(lat / cell)), ()):
        if i in near:
            continue
        # Flat-earth distance to the segment is fine at these distances.
        cos = math.cos(math.radians(lat))
        px, py = (lon - p[1]) * k * cos, (lat - p[0]) * k
        dx, dy = (q[1] - p[1]) * k * cos, (q[0] - p[0]) * k
        t = max(0.0, min(1.0, (px * dx + py * dy) / (dx * dx + dy * dy))) if dx or dy else 0.0
        if math.hypot(px - t * dx, py - t * dy) <= within_m:
            near.add(i)
    return near


def street_counts(nodes, edges, pois, cameras):
    """Per street: places open in each hour, plus all-day places, late help points, transit stops and cameras."""
    grid, cell = street_grid(nodes, edges)
    counts = [[0] * 24 for _ in edges]
    extra = [[0, 0, 0, 0] for _ in edges]  # all-day, late help, transit, cameras
    for poi in pois:
        for i in streets_near(grid, cell, poi["lat"], poi["lon"], POI_NEAR_M):
            row = counts[i]
            for hour in range(24):
                if poi["mask"] >> hour & 1:
                    row[hour] = min(255, row[hour] + 1)
            if poi["mask"] == FULL_DAY:
                extra[i][0] = min(255, extra[i][0] + 1)
            if poi["kind"] in HELP_KINDS and poi["mask"] & 1:  # still open after midnight
                extra[i][1] = min(255, extra[i][1] + 1)
            if poi["kind"] == "transit":
                extra[i][2] = min(255, extra[i][2] + 1)
    for lat, lon in cameras:
        for i in streets_near(grid, cell, lat, lon, CAMERA_NEAR_M):
            extra[i][3] = min(255, extra[i][3] + 1)
    return counts, extra


def e6(value):
    return int(round(value * 1_000_000))


def write_graph(nodes, edges, counts, extra, pois, meta):
    out = bytearray(b"RSG2")
    meta_bytes = json.dumps(meta, separators=(",", ":")).encode("utf-8")
    out += struct.pack(">H", len(meta_bytes)) + meta_bytes  # Java DataInput.readUTF
    out += struct.pack(">i", len(nodes))
    for lat, lon in nodes:
        out += struct.pack(">ii", e6(lat), e6(lon))
    out += struct.pack(">i", len(edges))
    for e, row, more in zip(edges, counts, extra):
        out += struct.pack(">iifbb", e["a"], e["b"], e["len"], e["cls"], e["flags"])
        out += bytes(row)
        out += bytes(more)
        out += struct.pack(">H", len(e["geom"]))
        for lat, lon in e["geom"]:
            out += struct.pack(">ii", e6(lat), e6(lon))
    out += struct.pack(">i", len(pois))
    for poi in pois:
        out += struct.pack(">iiib", e6(poi["lat"]), e6(poi["lon"]), poi["mask"], KINDS.index(poi["kind"]))
    GRAPH_OUT.write_bytes(bytes(out))


def write_demo_crime(nodes, edges):
    """SYNTHETIC zones for the demo. Not real crime data; no case counts on purpose."""
    rng = random.Random(CRIME_SEED)
    street_nodes = sorted({e["a"] for e in edges if e["cls"] <= 5})
    zones = []
    for i in range(CRIME_ZONES):
        lat, lon = nodes[rng.choice(street_nodes)]
        zones.append({
            "id": f"demo-{i + 1}",
            "name": f"Demo zone {i + 1}",
            "lat": round(lat, 6),
            "lon": round(lon, 6),
            "radiusM": rng.choice([250, 350, 450, 600]),
            "intensity": rng.choice([0.35, 0.6, 0.9]),
        })
    crime = {
        "demo": True,
        "title": "Demo crime layer (synthetic)",
        "notice": "Synthetic zones generated for the demo from a fixed random seed. "
                  "Not real crime statistics and not linked to any real neighbourhood.",
        "source": {
            "id": "demo_crime",
            "title": "Generated by tools/safety/build_safety.py",
            "url": "https://ncrb.gov.in/",
            "retrieved": RETRIEVED,
            "note": "Pune has no open street-level crime data; NCRB publishes city totals only. "
                    "Replace this file with official data in the same format.",
        },
        "period": None,
        "zones": zones,
    }
    CRIME_OUT.write_text(json.dumps(crime, indent=2) + "\n", encoding="utf-8")


def main():
    bbox = f"{SOUTH},{WEST},{NORTH},{EAST}"
    print("Fetching streets ...")
    streets = overpass("streets", f"""
        [out:json][timeout:240];
        way[highway~"^({'|'.join(CLASS_OF)})$"]({bbox});
        out body; >; out skel qt;
    """)
    nodes, edges = build_graph(streets)
    print(f"  {len(nodes)} graph nodes, {len(edges)} edges")

    print("Fetching places ...")
    places = overpass("places", f"""
        [out:json][timeout:240];
        (
          nwr[shop]({bbox});
          nwr[amenity~"^({'|'.join(AMENITY_KIND)})$"]({bbox});
          node[highway=bus_stop]({bbox});
          nwr[railway=station]({bbox});
        );
        out center tags;
    """)
    pois = build_pois(places)
    print(f"  {len(pois)} places, {sum(p['tagged'] for p in pois)} with opening hours in OSM")

    print("Fetching surveillance cameras ...")
    camera_data = overpass("cameras", f"""
        [out:json][timeout:120];
        node["man_made"="surveillance"]({bbox});
        out;
    """)
    cameras = build_cameras(camera_data)
    print(f"  {len(cameras)} cameras mapped in OSM")
    counts, extra = street_counts(nodes, edges, pois, cameras)

    meta = {
        "generatedOn": RETRIEVED,
        "bbox": [SOUTH, WEST, NORTH, EAST],
        "classes": CLASSES,
        "kinds": KINDS,
        "cameras": len(cameras),
        "hoursTagged": sum(p["tagged"] for p in pois),
        "sources": [{
            "id": "osm_streets",
            "title": "Streets and places from OpenStreetMap (Overpass API)",
            "url": "https://www.openstreetmap.org/copyright",
            "retrieved": RETRIEVED,
            "note": f"{len(edges)} street segments and {len(pois)} shops, food places, services and transit stops. "
                    "Map data (c) OpenStreetMap contributors, ODbL.",
        }, {
            "id": "opening_hours",
            "title": "Opening hours",
            "url": "https://wiki.openstreetmap.org/wiki/Key:opening_hours",
            "retrieved": RETRIEVED,
            "note": f"From OSM where tagged ({sum(p['tagged'] for p in pois)} places); otherwise typical hours "
                    "by type, e.g. shops 10:00-22:00, food 08:00-23:00. People activity is an estimate.",
        }, {
            "id": "osm_cameras",
            "title": "Surveillance cameras mapped in OpenStreetMap",
            "url": "https://wiki.openstreetmap.org/wiki/Tag:man_made%3Dsurveillance",
            "retrieved": RETRIEVED,
            "note": f"{len(cameras)} cameras mapped by volunteers. Coverage is incomplete, so a street with "
                    "no camera here is not necessarily unwatched.",
        }],
    }
    ASSETS.mkdir(parents=True, exist_ok=True)
    write_graph(nodes, edges, counts, extra, pois, meta)
    print(f"Wrote {GRAPH_OUT.relative_to(ROOT)} ({GRAPH_OUT.stat().st_size // 1024} KB)")
    if "--keep-crime" in sys.argv and CRIME_OUT.exists():
        print(f"Kept {CRIME_OUT.relative_to(ROOT)}")
    else:
        write_demo_crime(nodes, edges)
        print(f"Wrote {CRIME_OUT.relative_to(ROOT)} (DEMO data)")


if __name__ == "__main__":
    main()
