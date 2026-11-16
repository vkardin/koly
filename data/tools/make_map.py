"""Builds the region map used by the website and the desktop widget.

Input: Natural Earth admin-1 GeoJSON (public domain), e.g.
https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_1_states_provinces.geojson

    python tools/make_map.py ne_10m_admin_1_states_provinces.geojson ../web/ua-map.json

Output: SVG path data per region in a ~1000-unit-wide view box (equirectangular, scaled for
latitude 48.4), simplified with Douglas-Peucker. The whole of Ukraine is drawn within its
internationally recognized borders; temporarily occupied regions are marked `occupied`.
"""

from __future__ import annotations

import json
import math
import sys

ISO_TO_ID = {
    "UA-30": "kyiv-city", "UA-32": "kyiv", "UA-05": "vinnytsia", "UA-07": "volyn",
    "UA-12": "dnipropetrovsk", "UA-14": "donetsk", "UA-18": "zhytomyr", "UA-21": "zakarpattia",
    "UA-23": "zaporizhzhia", "UA-26": "ivano-frankivsk", "UA-35": "kirovohrad", "UA-46": "lviv",
    "UA-48": "mykolaiv", "UA-51": "odesa", "UA-53": "poltava", "UA-56": "rivne", "UA-59": "sumy",
    "UA-61": "ternopil", "UA-63": "kharkiv", "UA-65": "kherson", "UA-68": "khmelnytskyi",
    "UA-71": "cherkasy", "UA-77": "chernivtsi", "UA-74": "chernihiv",
    "UA-09": "luhansk", "UA-43": "crimea", "UA-40": "sevastopol",
}
OCCUPIED = {"luhansk", "crimea", "sevastopol"}
EXTRA_NAMES = {
    "luhansk": ("Луганська", "Luhansk", "Луганская"),
    "crimea": ("АР Крим", "Crimea", "АР Крым"),
    "sevastopol": ("Севастополь", "Sevastopol", "Севастополь"),
}
LON0, LAT0 = 22.0, 52.5
K = 82.0  # units per degree of latitude
COS = math.cos(math.radians(48.4))
TOL = 0.3  # simplification tolerance, in output units
MIN_AREA = 6.0  # drop islets smaller than this (square units)


def project(lon: float, lat: float) -> tuple[float, float]:
    return ((lon - LON0) * COS * K, (LAT0 - lat) * K)


def _dist(p, a, b) -> float:
    (x, y), (x1, y1), (x2, y2) = p, a, b
    dx, dy = x2 - x1, y2 - y1
    if dx == dy == 0:
        return math.hypot(x - x1, y - y1)
    t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)))
    return math.hypot(x - x1 - t * dx, y - y1 - t * dy)


def simplify(pts: list, tol: float) -> list:
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        i, j = stack.pop()
        best, idx = 0.0, -1
        for k in range(i + 1, j):
            d = _dist(pts[k], pts[i], pts[j])
            if d > best:
                best, idx = d, k
        if best > tol:
            keep[idx] = True
            stack += [(i, idx), (idx, j)]
    return [p for p, k in zip(pts, keep) if k]


def area(pts: list) -> float:
    return abs(sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]))) / 2


def centroid(pts: list) -> tuple[float, float]:
    a = cx = cy = 0.0
    for (x1, y1), (x2, y2) in zip(pts, pts[1:] + pts[:1]):
        c = x1 * y2 - x2 * y1
        a += c
        cx += (x1 + x2) * c
        cy += (y1 + y2) * c
    return (cx / (3 * a), cy / (3 * a)) if a else pts[0]


def ring_path(pts: list) -> str:
    out = []
    px = py = 0
    for i, (x, y) in enumerate(pts):
        xi, yi = round(x * 10), round(y * 10)
        if i == 0:
            out.append(f"M{xi / 10:g} {yi / 10:g}")
        else:
            out.append(f"l{(xi - px) / 10:g} {(yi - py) / 10:g}")
        px, py = xi, yi
    return "".join(out) + "z"


def main(src: str, dst: str) -> None:
    with open(src, encoding="utf-8") as f:
        features = json.load(f)["features"]
    regions = []
    for feat in features:
        iso = feat["properties"].get("iso_3166_2")
        rid = ISO_TO_ID.get(iso)
        if not rid:
            continue
        geom = feat["geometry"]
        polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        rings, biggest = [], (0.0, None)
        for poly in polys:
            for n, ring in enumerate(poly):
                pts = [project(lon, lat) for lon, lat in ring]
                pts = simplify(pts[:-1] if pts[0] == pts[-1] else pts, TOL)
                if len(pts) < 3:
                    continue
                a = area(pts)
                if a < MIN_AREA and rid not in ("kyiv-city", "sevastopol"):
                    continue
                rings.append(ring_path(pts))
                if n == 0 and a > biggest[0]:
                    biggest = (a, pts)
        cx, cy = centroid(biggest[1])
        entry = {"id": rid, "iso": iso, "d": "".join(rings), "label": [round(cx, 1), round(cy, 1)]}
        if rid in OCCUPIED:
            entry["occupied"] = True
            uk, en, ru = EXTRA_NAMES[rid]
            entry["name"] = {"uk": uk, "en": en, "ru": ru}
        regions.append(entry)
    # Small regions last so they are drawn on top and stay clickable.
    regions.sort(key=lambda r: r["id"] in ("kyiv-city", "sevastopol"))
    xs, ys = [], []
    for r in regions:
        xs.append(r["label"][0])
        ys.append(r["label"][1])
    out = {
        "source": "Natural Earth admin-1 (public domain), simplified",
        "viewBox": [0, 0, round((40.3 - LON0) * COS * K), round((LAT0 - 44.25) * K)],
        "regions": regions,
    }
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(regions)} regions, {sum(len(r['d']) for r in regions)} bytes of path data")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
