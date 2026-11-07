"""Opens, updates and closes GitHub issues when published data needs attention.

Usage (in the data workflow):
    python monitor.py --site ../site            after a run: stale regions
    python monitor.py --workflow-failed URL     when the workflow failed

Needs GITHUB_TOKEN and GITHUB_REPOSITORY. Without them it only prints the report.
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from datetime import datetime, timedelta
from pathlib import Path

import httpx

from common.http import USER_AGENT
from common.models import RegionFile
from common.timeutil import now_kyiv

log = logging.getLogger("koly.monitor")

STALE_AFTER = timedelta(hours=int(os.environ.get("KOLY_STALE_ALERT_HOURS", "6")))
LABEL = "data-alert"
STALE_TITLE = "Data sources need attention"
FAILED_TITLE = "Data workflow is failing"
MARKER = "<!-- koly-monitor:{key} -->"


def stale_regions(site: Path, now: datetime, stale_after: timedelta = STALE_AFTER) -> list[str]:
    """Lines describing regions that have been stale for longer than `stale_after`."""
    lines = []
    for path in sorted((site / "v1" / "regions").glob("*.json")):
        try:
            region = RegionFile.model_validate_json(path.read_text(encoding="utf-8"))
        except ValueError:
            lines.append(f"- `{path.name}` is not valid against the schema")
            continue
        age = now - datetime.fromisoformat(region.fetchedAt)
        if region.status == "stale" and age >= stale_after:
            # Keep the line stable between runs so the issue is not edited every time.
            lines.append(
                f"- **{region.id}** ({region.provider}): last successful fetch "
                f"{region.fetchedAt}. Source: {region.sourceUrl}"
            )
    return lines


class Issues:
    def __init__(self, token: str, repo: str):
        self.client = httpx.Client(
            base_url=f"https://api.github.com/repos/{repo}",
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "User-Agent": USER_AGENT,
            },
            timeout=20,
        )

    def find_open(self, key: str) -> dict | None:
        response = self.client.get("/issues", params={"labels": LABEL, "state": "open", "per_page": 50})
        response.raise_for_status()
        marker = MARKER.format(key=key)
        return next((i for i in response.json() if marker in (i.get("body") or "")), None)

    def open_or_update(self, key: str, title: str, body: str) -> None:
        body = f"{MARKER.format(key=key)}\n{body}"
        issue = self.find_open(key)
        if issue is None:
            self.client.post("/issues", json={"title": title, "body": body, "labels": [LABEL]}).raise_for_status()
            log.info("opened issue: %s", title)
        elif issue["body"].strip() != body.strip():
            number = issue["number"]
            self.client.patch(f"/issues/{number}", json={"body": body}).raise_for_status()
            self.client.post(f"/issues/{number}/comments", json={"body": "The list above changed."}).raise_for_status()
            log.info("updated issue #%s", number)

    def close(self, key: str, comment: str) -> None:
        issue = self.find_open(key)
        if issue is None:
            return
        number = issue["number"]
        self.client.post(f"/issues/{number}/comments", json={"body": comment}).raise_for_status()
        self.client.patch(f"/issues/{number}", json={"state": "closed"}).raise_for_status()
        log.info("closed issue #%s", number)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", type=Path)
    parser.add_argument("--workflow-failed", metavar="RUN_URL")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")

    token, repo = os.environ.get("GITHUB_TOKEN"), os.environ.get("GITHUB_REPOSITORY")
    issues = Issues(token, repo) if token and repo else None

    if args.workflow_failed:
        body = (
            "The scheduled data run failed, so schedules are not being updated.\n\n"
            f"Latest failed run: {args.workflow_failed}\n\n"
            "This issue closes itself after the next successful run."
        )
        print(body)
        if issues:
            issues.open_or_update("workflow", FAILED_TITLE, body)
        return 0

    if not args.site:
        parser.error("--site is required")
    lines = stale_regions(args.site, now_kyiv())
    if issues:
        issues.close("workflow", "The data workflow succeeded again.")
    if lines:
        body = (
            f"These regions could not be refreshed for more than {int(STALE_AFTER.total_seconds() // 3600)} hours. "
            "Apps keep showing the last valid schedule marked as outdated.\n\n"
            + "\n".join(lines)
            + "\n\nCheck whether the source changed its format or blocks requests. "
            "See `data/SOURCES.md`. This issue closes itself when all regions recover."
        )
        print(body)
        if issues:
            issues.open_or_update("stale", STALE_TITLE, body)
    else:
        log.info("no stale regions")
        if issues:
            issues.close("stale", "All regions are fresh again.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
