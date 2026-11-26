"""Collects the groups of addresses whose directory answers only connections from Ukraine.

Run on any computer in Ukraine, whenever it has power and internet; each run continues where
the last one stopped. The addresses come from the published house tiles, the answers go to the
same cache the GitHub workflow uses (a clone of the `houses-cache` branch):

    python -m houses.local --cache ../../koly-cache/cache --crawler poltava --hours 2

tools/collect-addresses.ps1 does all of it on Windows, including saving the result to GitHub.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import math
from pathlib import Path

import httpx
from shapely.geometry import Point, shape
from shapely.prepared import prep

from common.http import USER_AGENT

from .crawl import CRAWLERS, Address, crawl
from .osm import REGIONS_FILE

SITE = "https://vkardin.github.io/koly/v1/houses/"
LON0, LAT0, K = 22.0, 52.5, 82.0
COS = math.cos(math.radians(48.4))


def addresses(region: str) -> list[Address]:
    """Every published building of `region`, as the address the directories are asked about."""
    geo = json.loads(REGIONS_FILE.read_text(encoding="utf-8"))
    area = shape(next(f["geometry"] for f in geo["features"] if f["properties"]["id"] == region))
    inside = prep(area)
    w, s, e, n = area.bounds
    with httpx.Client(headers={"User-Agent": USER_AGENT}, timeout=30) as http:
        index = http.get(SITE + "index.json").raise_for_status().json()
        size, q = index["tileSize"], index["q"]
        x0, x1 = (w - LON0) * COS * K, (e - LON0) * COS * K
        y0, y1 = (LAT0 - n) * K, (LAT0 - s) * K
        out: list[Address] = []
        for key in index["tiles"]:
            tx, ty = map(int, key.split("_"))
            if not (x0 - size <= tx * size <= x1 and y0 - size <= ty * size <= y1):
                continue
            t = http.get(SITE + key + ".json").raise_for_status().json()
            x = y = 0
            for i in range(len(t["x"])):
                x += t["x"][i]
                y += t["y"][i]
                lon = (tx * size + x / q) / (COS * K) + LON0
                lat = LAT0 - (ty * size + y / q) / K
                if inside.contains(Point(lon, lat)):
                    out.append(Address(region, t["cities"][t["c"][i]], t["streets"][t["s"][i]], "", t["n"][i]))
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--crawler", default="poltava", choices=sorted(c.id for c in CRAWLERS.values() if c.local))
    parser.add_argument("--hours", type=float, default=2)
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    crawler = CRAWLERS[args.crawler]
    found = addresses(crawler.region)
    logging.info("%d published addresses in %s", len(found), crawler.region)
    summary = asyncio.run(crawl(crawler, found, args.cache, args.hours * 3600, 75))
    print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
