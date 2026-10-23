# Street safety data (shortest vs safety route)

Raasta shows two versions of every option on the route screen:

- **Shortest**: walking and auto legs follow the shortest streets (Dijkstra on distance), like a normal map app.
- **Safety route**: the same Dijkstra, but each street costs more if it is in a crime zone or likely to be empty at that hour:

  `cost = length × (1 + 4 × crime + E × (1 − crowd) × (1 − help) + D × dark)`, with `E` = 1.5 by day and 2.5 from 8 PM to 6 AM, `D` = 0.5 by day and 1.5 at night, and `dark` = 1 for a dark street (see below).

  The safety route may be at most 40% longer than the shortest one (+150 m for short walks). If no route fits, the penalty is softened, and if that fails the shortest route is shown. A different route is only offered if its safety score is at least 2 points higher.

Bus and metro legs are not changed. A longer walk before a timetabled bus only uses up the waiting time; if it would miss the bus, the shortest streets are kept for that walk.

## Safety score

For each street, `risk = (0.6 × crime + 0.4 × (1 − crowd)) × (1 − help)` (only `1 − crowd` when there is no crime file). The score is `100 × (1 − (risk + dark gaps) / length)`, averaged over the length of the walking and auto legs. The route screen also shows the share of street length inside crime zones and on busy streets (crowd ≥ 0.5).

**Night activity** (the idea behind the NightWise reel) adds three things to the score:

- **Help** lowers a street's risk by up to 35%. It comes from help points that stay open after midnight (police, hospital, fuel station, pharmacy), places open all 24 hours, cameras and transit stops within 40 to 50 m. It counts fully at night and 40% by day.
- **Dark gaps** are runs of side streets with no open place, no camera and no transit stop. Main roads (trunk, primary, secondary) are never dark because traffic keeps them busy. A run of 150 m or more adds extra risk (0.5 per metre walking, 0.25 driving). The longest dark run is shown next to each route, so a route with a few shops bunched together and a long empty stretch scores lower than one with the same shops spread along it.
- **Open places** along the route at the time you pass are counted. The count is per street, so a place beside a junction can count twice; the app says "about".

**Crowd** (0–1) is an estimate, not a measurement: road type × time-of-day factor, plus the shops, food places, services and transit stops within 40 m that are open at that hour.

## Files

| File | What it is | Source |
|---|---|---|
| `app/app/src/main/assets/street_graph.bin` (format `RSG2`) | Street graph (about 33,000 segments), about 3,400 places with kind and opening hours, and per-street counts of all-day places, late help points, transit stops and cameras | OpenStreetMap via Overpass API, ODbL |
| `app/app/src/main/assets/crime_zones.json` | **Demo crime layer: synthetic, not real data** | Generated from a fixed random seed |

Rebuild both (standard library Python only; Overpass replies are cached in `.cache/`):

```bash
python -I tools/safety/build_safety.py
```

Add `--keep-crime` to rebuild the streets without overwriting an imported crime file. Delete `.cache/` to download fresh OpenStreetMap data.

## Limitations

- **Crime data is synthetic.** Pune has no open street-level crime data: NCRB publishes city totals, and police-station-wise counts and boundaries are not published as open data. The app labels the layer "demo" everywhere it appears, and the zones have no case counts.
- **Opening hours** are tagged for only about 1 in 10 places in OSM. The rest use typical hours by type (shops 10:00–22:00, food 08:00–23:00, banks 10:00–17:00, hospitals, fuel, police and ATMs all day, transit stops 06:00–23:00).
- **Cameras** are the ones volunteers have mapped in OSM (`man_made=surveillance`): 18 in the whole corridor. A street with no camera here is not necessarily unwatched, so cameras only ever raise a score a little and never lower it.
- **Street lighting** is not used: OSM `lit` tags cover about 2% of roads in the corridor.
- Coverage is the Kharadi to Kothrud demo corridor only.
- "Safer" means safer by this data. It is not a guarantee.

## Importing real crime data

Replace `crime_zones.json` with the same format and set `"demo": false`. Each zone is either a circle or a polygon, for example a police station jurisdiction converted from GeoJSON (coordinates as `[lat, lon]`):

```json
{
  "demo": false,
  "title": "Pune City Police, crimes against women by police station",
  "notice": "Area-level reported cases; not exact incident locations.",
  "period": "2025",
  "source": {"id": "pune_police_2025", "title": "Pune City Police", "url": "https://...", "retrieved": "2026-11-01", "note": "Obtained by RTI"},
  "zones": [
    {"id": "ps-01", "name": "Example police station", "intensity": 0.8, "reportedCases": 412,
     "category": "Crimes against women", "polygon": [[18.55, 73.93], [18.56, 73.94], [18.54, 73.95]]},
    {"id": "spot-1", "name": "Example hotspot", "intensity": 0.6, "lat": 18.53, "lon": 73.87, "radiusM": 300}
  ]
}
```

- `intensity` is 0–1, relative to the highest area in the data. When converting counts, decide whether to use raw reported counts or a population-adjusted rate, and say which one in `title` or `notice`.
- Zones that fail validation (intensity outside 0–1, fewer than 3 polygon points, missing coordinates, negative counts) are skipped; `CrimeLayer.rejected` lists them with the reason.
- The tapped-zone card shows the zone's category, reported cases, the period, the notice and the source.
