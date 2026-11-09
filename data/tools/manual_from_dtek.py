"""Turn a saved DTEK "shutdowns" page into a manual schedule file.

When the page cannot be fetched automatically (bot protection), open it in a
browser, save it (Ctrl+S, "Webpage, HTML only") and run from `data/`:

    python tools/manual_from_dtek.py page.html dnipropetrovsk

This writes `manual/dnipropetrovsk.json` with every day the page has. Commit it
to `main`; the next data run publishes it while the automatic source has no
schedule (see manual/README.md).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from common.models import RegionFile  # noqa: E402
from common.regions import REGION_NAMES  # noqa: E402
from providers import _dtek_site  # noqa: E402
from providers.manual import MANUAL_DIR  # noqa: E402

SITES = {
    "dnipropetrovsk": "https://www.dtek-dnem.com.ua/ua/shutdowns",
    "kyiv-city": "https://www.dtek-kem.com.ua/ua/shutdowns",
    "kyiv": "https://www.dtek-krem.com.ua/ua/shutdowns",
    "odesa": "https://www.dtek-oem.com.ua/ua/shutdowns",
    "donetsk": "https://www.dtek-dem.com.ua/ua/shutdowns",
}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("page", type=Path, help="saved HTML of the DTEK shutdowns page")
    parser.add_argument("region", choices=sorted(r for r in SITES if r in REGION_NAMES))
    parser.add_argument("--out", type=Path, default=None, help="default: manual/<region>.json")
    args = parser.parse_args(argv)

    schedule = _dtek_site.parse(args.page.read_text(encoding="utf-8", errors="replace"), args.region)
    body = {
        "sourceUrl": SITES[args.region],
        "updatedAt": schedule.updated_at,
        "days": [day.model_dump(by_alias=True) for day in schedule.days],
    }
    # Same checks as publishing, so a bad page never produces a file that would be rejected.
    RegionFile.model_validate(
        {"id": args.region, "provider": "manual", "sourceUrl": body["sourceUrl"], "fetchedAt": body["updatedAt"],
         "updatedAt": body["updatedAt"], "status": "ok", "days": body["days"]}
    )
    out = args.out or MANUAL_DIR / f"{args.region}.json"
    out.write_text(json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    days = ", ".join(d.date for d in schedule.days)
    print(f"{out}: {days} (updated {schedule.updated_at})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
