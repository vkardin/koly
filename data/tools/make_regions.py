"""Region shapes for the power map (lon/lat GeoJSON), from data/houses/regions.geojson.

    python tools/make_regions.py ../web/ua-regions.json

Each region keeps its id and whether it is temporarily occupied; label points are where
the map writes the region's name. Natural Earth admin-1 (public domain), simplified.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from shapely.geometry import mapping, shape

SRC = Path(__file__).resolve().parent.parent / "houses" / "regions.geojson"
# Names sit badly at the representative point for a few regions; nudge them (lon, lat).
NUDGE = {"kyiv": (30.0, 50.75), "kyiv-city": (30.52, 50.45)}


def main(out: str) -> None:
    data = json.loads(SRC.read_text(encoding="utf-8"))
    shapes, labels = [], []
    for f in data["features"]:
        rid, occ = f["properties"]["id"], f["properties"]["occupied"]
        geom = shape(f["geometry"]).simplify(0.004, preserve_topology=True)
        g = mapping(geom)

        def rnd(x):
            return [round(x[0], 4), round(x[1], 4)] if isinstance(x[0], (int, float)) else [rnd(y) for y in x]

        shapes.append({"type": "Feature", "properties": {"id": rid, "occupied": occ},
                       "geometry": {"type": g["type"], "coordinates": rnd(g["coordinates"])}})
        if rid == "sevastopol":
            continue
        p = NUDGE.get(rid) or tuple(geom.representative_point().coords[0])
        labels.append({"type": "Feature", "properties": {"id": rid, "small": rid == "kyiv-city"},
                       "geometry": {"type": "Point", "coordinates": [round(p[0], 3), round(p[1], 3)]}})
    Path(out).write_text(json.dumps({"source": "Natural Earth admin-1 (public domain), simplified",
                                     "shapes": {"type": "FeatureCollection", "features": shapes},
                                     "labels": {"type": "FeatureCollection", "features": labels}},
                                    ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1])
