"""Fetches all providers in parallel and updates the static site.

Usage: python run.py --site ../site [--only dtek-kem,dtek-oem] [--report report.json]
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

from common.http import NotPublished
from common.models import RegionFile
from common.publish import ProviderResult, publish
from common.timeutil import iso_kyiv, now_kyiv
from providers import Provider, all_providers

log = logging.getLogger("koly.run")


def is_due(provider: Provider, site: Path, now: datetime) -> bool:
    """Providers with `min_interval` are skipped while their published data is fresh."""
    interval = getattr(provider, "min_interval", None)
    if interval is None:
        return True
    for region_id in provider.region_ids:
        path = site / "v1" / "regions" / f"{region_id}.json"
        try:
            region = RegionFile.model_validate_json(path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return True
        if region.status == "stale" or now - datetime.fromisoformat(region.fetchedAt) >= interval:
            return True
    return False


def run_provider(provider: Provider, due: bool) -> ProviderResult:
    result = ProviderResult(provider.id, provider.source_url, list(provider.region_ids))
    if not due:
        result.skipped = True
        return result
    try:
        result.schedules = provider.fetch()
    except NotPublished as error:
        result.error = f"not published: {error}"
        result.not_published = True
    except Exception as error:  # one provider must never break the others
        result.error = f"{type(error).__name__}: {error}"
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", type=Path, required=True, help="published site directory")
    parser.add_argument("--only", default="", help="comma-separated provider ids")
    parser.add_argument("--report", type=Path, help="write a JSON run report here")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    now = now_kyiv()
    only = {p for p in args.only.split(",") if p}
    providers = [p for p in all_providers() if not only or p.id in only]
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda p: run_provider(p, is_due(p, args.site, now)), providers))

    for result in results:
        if result.skipped:
            state = "skipped (not due)"
        elif result.error:
            state = result.error
        else:
            state = f"{len(result.schedules)} region(s)"
        log.info("%s %s: %s", result.provider_id, ",".join(result.region_ids), state)

    report = publish(args.site, results, now)
    summary = [
        f"written: {', '.join(report.written) or 'nothing'}",
        f"stale: {', '.join(report.stale) or 'none'}",
        f"failed without data: {', '.join(report.failed) or 'none'}",
    ]
    for line in summary:
        log.info(line)
    if args.report:
        args.report.write_text(
            json.dumps(
                {
                    "ranAt": iso_kyiv(now),
                    "providers": [
                        {
                            "id": r.provider_id,
                            "regions": r.region_ids,
                            "skipped": r.skipped,
                            "error": r.error,
                            "notPublished": r.not_published,
                        }
                        for r in results
                    ],
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as out:
            out.write("\n".join(f"- {line}" for line in summary) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
