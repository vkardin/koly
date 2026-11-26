"""Addressed buildings and streets from an OpenStreetMap extract of Ukraine.

    python -m houses.osm ukraine-latest.osm.pbf out/

Writes `out/buildings.tsv.gz` (region, lon, lat, city, street, old street name, number) and
`out/roads.tsv.gz` (class, lon/lat pairs). Only buildings with a street and a house number are
kept; the city is the `addr:city` tag, or nothing for Kyiv city, whose directory needs none.
A building without `addr:city` elsewhere is kept for the map but never looked up: guessing the
settlement from the nearest village could give it another village's group.

Map data © OpenStreetMap contributors, ODbL.
"""

from __future__ import annotations

import argparse
import gzip
import json
import logging
from pathlib import Path

import osmium
from shapely.geometry import Point, shape
from shapely.prepared import prep
from shapely.strtree import STRtree

log = logging.getLogger("koly.houses.osm")

REGIONS_FILE = Path(__file__).with_name("regions.geojson")
# Streets drawn under the houses, from the biggest down. Service roads and paths are left out.
ROAD_CLASSES = {
    "motorway": 1, "trunk": 1, "primary": 1, "secondary": 2, "tertiary": 2,
    "residential": 3, "unclassified": 3, "living_street": 3,
}
NO_CITY_NEEDED = {"kyiv-city"}


class Regions:
    """Point → region id, for the regions that are not occupied."""

    def __init__(self, path: Path = REGIONS_FILE) -> None:
        data = json.loads(path.read_text(encoding="utf-8"))
        feats = [f for f in data["features"] if not f["properties"]["occupied"]]
        # Kyiv city lies inside Kyiv region: test it first.
        feats.sort(key=lambda f: f["properties"]["id"] != "kyiv-city")
        self.ids = [f["properties"]["id"] for f in feats]
        self.shapes = [shape(f["geometry"]) for f in feats]
        self.prepared = [prep(s) for s in self.shapes]
        self.tree = STRtree(self.shapes)

    def at(self, lon: float, lat: float) -> str | None:
        p = Point(lon, lat)
        hits = sorted(int(i) for i in self.tree.query(p))
        for i in hits:
            if self.prepared[i].contains(p):
                return self.ids[i]
        return None


def _clean(value: str | None) -> str:
    return " ".join((value or "").replace("\t", " ").split())


class Handler(osmium.SimpleHandler):
    def __init__(self, regions: Regions, buildings, roads) -> None:
        super().__init__()
        self.regions = regions
        self.buildings = buildings
        self.roads = roads
        self.seen: set[tuple[str, str, str]] = set()
        self.count = 0

    def _address(self, tags, lon: float, lat: float) -> None:
        number = _clean(tags.get("addr:housenumber"))
        street = _clean(tags.get("addr:street") or tags.get("addr:place"))
        if not number or not street or len(number) > 20:
            return
        region = self.regions.at(lon, lat)
        if region is None:
            return
        city = "" if region in NO_CITY_NEEDED else _clean(tags.get("addr:city"))
        key = (city or f"{lon:.3f},{lat:.3f}", street.casefold(), number.casefold())
        if key in self.seen:  # an entrance node and its building: keep one point
            return
        self.seen.add(key)
        old = _clean(tags.get("old_addr:street"))
        self.buildings.write(f"{region}\t{lon:.6f}\t{lat:.6f}\t{city}\t{street}\t{old}\t{number}\n")
        self.count += 1
        if self.count % 200_000 == 0:
            log.info("%d addresses", self.count)

    def node(self, n) -> None:
        if "addr:housenumber" in n.tags and n.location.valid():
            self._address(n.tags, n.location.lon, n.location.lat)

    def way(self, w) -> None:
        tags = w.tags
        if "building" in tags and "addr:housenumber" in tags:
            pts = [(nd.lon, nd.lat) for nd in w.nodes if nd.location.valid()]
            if len(pts) >= 3:
                if pts[0] == pts[-1]:
                    pts = pts[:-1]
                lon = sum(p[0] for p in pts) / len(pts)
                lat = sum(p[1] for p in pts) / len(pts)
                self._address(tags, lon, lat)
            return
        cls = ROAD_CLASSES.get(tags.get("highway", ""))
        if cls:
            pts = [f"{nd.lon:.5f},{nd.lat:.5f}" for nd in w.nodes if nd.location.valid()]
            if len(pts) >= 2:
                self.roads.write(f"{cls}\t{' '.join(pts)}\n")


def extract(pbf: Path, out: Path) -> int:
    out.mkdir(parents=True, exist_ok=True)
    regions = Regions()
    with gzip.open(out / "buildings.tsv.gz", "wt", encoding="utf-8") as b, \
            gzip.open(out / "roads.tsv.gz", "wt", encoding="utf-8") as r:
        b.write("region\tlon\tlat\tcity\tstreet\told_street\tnumber\n")
        handler = Handler(regions, b, r)
        handler.apply_file(str(pbf), locations=True, idx="flex_mem")
    log.info("%d addresses in all", handler.count)
    return handler.count


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("pbf", type=Path)
    parser.add_argument("out", type=Path)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    extract(args.pbf, args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
