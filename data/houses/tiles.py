"""Builds the house layer of the power map: small tiles of buildings with their group.

    python -m houses.tiles --osm out/ --cache cache/ --site ../site

Tiles are squares of TILE map units (an equirectangular projection scaled for latitude 48.4, about 11 km) in
`v1/houses/<tx>_<ty>.json`, listed in `v1/houses/index.json`. A tile holds every addressed
building in it (a point, its street and number) and, where an operator's directory gave one,
its group; plus the streets to draw under them. Clients colour each building by its group's
schedule right now, so tiles change only when the buildings or the directory answers do.

Map data © OpenStreetMap contributors, ODbL. The published tiles are a derived database under
the same licence.
"""

from __future__ import annotations

import argparse
import gzip
import json
import logging
import math
import os
from collections import defaultdict
from pathlib import Path

from shapely.geometry import LineString

from .crawl import CRAWLERS, Address, load_cache, read_buildings

log = logging.getLogger("koly.houses.tiles")

# Same projection as data/tools/make_map.py.
LON0, LAT0, K = 22.0, 52.5, 82.0
COS = math.cos(math.radians(48.4))
TILE = 8  # map units per tile side
Q = 1024  # building positions: 1/Q of a unit (~1.3 m)
QR = 256  # street vertices: 1/QR of a unit (~5 m)


def project(lon: float, lat: float) -> tuple[float, float]:
    return (lon - LON0) * COS * K, (LAT0 - lat) * K


def _groups_for(addr: Address, caches: dict[str, dict[str, dict]]) -> tuple[str, tuple[str, ...]] | None:
    """(schedule region, groups) from the first crawler of the building's region that placed it."""
    for crawler in CRAWLERS.values():
        if crawler.region != addr.region:
            continue
        entry = caches.get(crawler.id, {}).get(addr.key)
        if entry and entry.get("g"):
            return crawler.publishes_as, tuple(entry["g"])
    return None


def _write(path: Path, data: dict) -> bool:
    raw = json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n"
    if path.exists() and path.read_text(encoding="utf-8") == raw:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(raw, encoding="utf-8")
    os.replace(tmp, path)
    return True


def build(osm: Path, cache_dir: Path, site: Path, generated_at: str) -> dict:
    caches = {cid: load_cache(cache_dir / f"{cid}.json.gz") for cid in CRAWLERS}
    # Only regions with an open address directory: elsewhere every building would be "group unknown",
    # and the map keeps showing the region instead.
    covered = {c.region for c in CRAWLERS.values()}
    tiles: dict[tuple[int, int], list] = defaultdict(list)
    coverage: dict[str, dict[str, int]] = defaultdict(lambda: {"houses": 0, "withGroup": 0})
    for addr, lon, lat in read_buildings(osm / "buildings.tsv.gz"):
        if addr.region not in covered:
            continue
        x, y = project(lon, lat)
        key = (math.floor(x / TILE), math.floor(y / TILE))
        found = _groups_for(addr, caches)
        tiles[key].append((x, y, addr, found))
        coverage[addr.region]["houses"] += 1
        if found:
            coverage[addr.region]["withGroup"] += 1

    roads: dict[tuple[int, int], list] = defaultdict(list)
    with gzip.open(osm / "roads.tsv.gz", "rt", encoding="utf-8") as f:
        for line in f:
            cls, _, coords = line.rstrip("\n").partition("\t")
            pts = [project(*map(float, p.split(","))) for p in coords.split(" ")]
            if len(pts) > 2:
                pts = list(LineString(pts).simplify(0.5 / QR).coords)
            touched = {(math.floor(px / TILE), math.floor(py / TILE)) for px, py in pts}
            for t in touched:
                if t in tiles:
                    roads[t].append((int(cls), pts))

    out_dir = site / "v1" / "houses"
    written = 0
    # Street index for the map's address search: (region, city, street) -> tiles it is in.
    street_tiles: dict[tuple[str, str, str], dict[str, int]] = defaultdict(dict)
    names: set[str] = set()
    for (tx, ty), houses in tiles.items():
        ox, oy = tx * TILE, ty * TILE
        houses.sort(key=lambda h: (h[1], h[0]))
        regions: list[str] = []
        group_sets: dict[tuple[int, tuple[str, ...]], int] = {}
        streets: dict[str, int] = {}
        cities: dict[str, int] = {}
        xs, ys, gs, ss, cs, ns = [], [], [], [], [], []
        px = py = 0
        for x, y, addr, found in houses:
            qx, qy = round((x - ox) * Q), round((y - oy) * Q)
            xs.append(qx - px)
            ys.append(qy - py)
            px, py = qx, qy
            if found:
                region, groups = found
                if region not in regions:
                    regions.append(region)
                gs.append(group_sets.setdefault((regions.index(region), groups), len(group_sets)))
            else:
                gs.append(-1)
            ss.append(streets.setdefault(addr.street, len(streets)))
            st = street_tiles[(addr.region, addr.city, addr.street)]
            st[f"{tx}_{ty}"] = st.get(f"{tx}_{ty}", 0) + 1
            cs.append(cities.setdefault(addr.city, len(cities)))
            ns.append(addr.number)
        lines = []
        for cls, pts in sorted(roads.get((tx, ty), []), key=lambda r: -r[0]):
            flat = [cls]
            lx = ly = None
            for vx, vy in pts:
                qx, qy = round((vx - ox) * QR), round((vy - oy) * QR)
                if lx is None:
                    flat += [qx, qy]
                elif (qx, qy) != (lx, ly):
                    flat += [qx - lx, qy - ly]
                lx, ly = qx, qy
            if len(flat) >= 5:
                lines.append(flat)
        data = {
            "v": 1, "tile": [tx, ty],
            "regions": regions,
            "groups": [[ri, *g] for (ri, g) in group_sets],
            "cities": list(cities), "streets": list(streets),
            "x": xs, "y": ys, "g": gs, "c": cs, "s": ss, "n": ns,
            "roads": lines,
        }
        name = f"{tx}_{ty}.json"
        names.add(name)
        written += _write(out_dir / name, data)
    for old in out_dir.glob("*_*.json"):
        if old.name not in names:
            old.unlink()
    index = {
        "v": 1, "tileSize": TILE, "q": Q, "qRoads": QR,
        "projection": {"lon0": LON0, "lat0": LAT0, "k": K, "cosLat": round(COS, 9)},
        "attribution": "© OpenStreetMap contributors (ODbL)",
        "streets": "streets.json",
        "tiles": {f"{tx}_{ty}": len(h) for (tx, ty), h in sorted(tiles.items())},
        "coverage": {r: coverage[r] for r in sorted(coverage)},
    }
    region_list = sorted({r for r, _, _ in street_tiles})
    items = sorted(street_tiles.items(), key=lambda kv: -sum(kv[1].values()))
    written += _write(out_dir / "streets.json", {
        "v": 1, "regions": region_list,
        "items": [[region_list.index(r), c, st, sorted(t, key=lambda k: -t[k])] for (r, c, st), t in items],
    })
    names.add("streets.json")
    index_path = out_dir / "index.json"
    try:
        old = json.loads(index_path.read_text(encoding="utf-8"))
        old.pop("generatedAt", None)
    except (OSError, ValueError):
        old = None
    if old != index or written:
        _write(index_path, {**index, "generatedAt": generated_at})
    log.info("%d tiles, %d rewritten; coverage %s", len(tiles), written, dict(coverage))
    return index


def main(argv: list[str] | None = None) -> int:
    from common.timeutil import iso_kyiv, now_kyiv

    parser = argparse.ArgumentParser()
    parser.add_argument("--osm", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--site", type=Path, required=True)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    build(args.osm, args.cache, args.site, iso_kyiv(now_kyiv()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
