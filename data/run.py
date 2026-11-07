"""Fetches all providers in parallel and updates the static site.

Usage: python run.py --site ../site [--only dtek-kem,dtek-oem]
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from common.publish import ProviderResult, publish
from common.timeutil import now_kyiv
from providers import PROVIDERS, Provider

log = logging.getLogger("koly.run")


def run_provider(provider: Provider) -> ProviderResult:
    result = ProviderResult(provider.id, provider.source_url, list(provider.region_ids))
    try:
        result.schedules = provider.fetch()
    except Exception as error:  # one provider must never break the others
        result.error = f"{type(error).__name__}: {error}"
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", type=Path, required=True, help="published site directory")
    parser.add_argument("--only", default="", help="comma-separated provider ids")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    only = {p for p in args.only.split(",") if p}
    providers = [p for p in PROVIDERS if not only or p.id in only]
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(run_provider, providers))

    for result in results:
        state = f"error: {result.error}" if result.error else f"{len(result.schedules)} region(s)"
        log.info("%s %s", result.provider_id, state)

    report = publish(args.site, results, now_kyiv())
    summary = [
        f"written: {', '.join(report.written) or 'nothing'}",
        f"stale: {', '.join(report.stale) or 'none'}",
        f"failed without data: {', '.join(report.failed) or 'none'}",
    ]
    for line in summary:
        log.info(line)
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a", encoding="utf-8") as out:
            out.write("\n".join(f"- {line}" for line in summary) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
