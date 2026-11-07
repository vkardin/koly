"""Merges fresh provider results with the published site and writes changed files.

Rules:
- A file is rewritten only when its content changed (compared by hash, ignoring
  fetch/generation times), or when the last fetch time is older than HEARTBEAT,
  so clients can tell fresh data from a stopped pipeline.
- A failed provider never replaces published data: the last valid file is kept
  and marked "stale".
- Anything written is validated by the schema models first.
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Iterable

from pydantic import ValidationError

from .models import IndexFile, RegionFile, RegionIndexEntry, RegionSchedule
from .regions import REGION_NAMES, REGION_ORDER
from .timeutil import iso_kyiv

log = logging.getLogger("koly.publish")

HEARTBEAT = timedelta(minutes=60)


@dataclass
class ProviderResult:
    provider_id: str
    source_url: str
    region_ids: list[str]
    schedules: list[RegionSchedule] = field(default_factory=list)
    error: str | None = None


@dataclass
class PublishReport:
    written: list[str] = field(default_factory=list)
    stale: list[str] = field(default_factory=list)
    failed: list[str] = field(default_factory=list)


def _dump(model) -> dict:
    return model.model_dump(mode="json", by_alias=True)


def _content_hash(data: dict, ignore: Iterable[str]) -> str:
    trimmed = {k: v for k, v in data.items() if k not in set(ignore)}
    raw = json.dumps(trimmed, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8"
    )
    os.replace(tmp, path)


def _load_region(path: Path) -> RegionFile | None:
    if not path.exists():
        return None
    try:
        return RegionFile.model_validate_json(path.read_text(encoding="utf-8"))
    except (ValidationError, ValueError) as error:
        log.warning("published %s is invalid, ignoring it: %s", path.name, error)
        return None


def _older_than(stamp: str, now: datetime, age: timedelta) -> bool:
    return now - datetime.fromisoformat(stamp) >= age


def publish(site: Path, results: list[ProviderResult], now: datetime) -> PublishReport:
    v1 = site / "v1"
    regions_dir = v1 / "regions"
    report = PublishReport()
    now_iso = iso_kyiv(now)
    current: dict[str, RegionFile] = {}

    for path in sorted(regions_dir.glob("*.json")) if regions_dir.exists() else []:
        region = _load_region(path)
        if region:
            current[region.id] = region

    for result in results:
        fresh = {s.region_id: s for s in result.schedules} if not result.error else {}
        for region_id in result.region_ids:
            previous = current.get(region_id)
            schedule = fresh.get(region_id)
            candidate: RegionFile | None = None
            if schedule is not None:
                try:
                    candidate = RegionFile(
                        id=region_id,
                        provider=result.provider_id,
                        sourceUrl=result.source_url,
                        fetchedAt=now_iso,
                        updatedAt=schedule.updated_at,
                        status="no_schedules" if schedule.no_schedules else "ok",
                        emergency=schedule.emergency,
                        days=schedule.days,
                    )
                except ValidationError as error:
                    log.error("%s: schedule failed validation: %s", region_id, error)
            if candidate is None:
                reason = result.error or "no valid schedule for region"
                log.error("%s/%s failed: %s", result.provider_id, region_id, reason)
                if previous is None:
                    report.failed.append(region_id)
                    continue
                report.stale.append(region_id)
                if previous.status == "stale":
                    continue
                candidate = previous.model_copy(update={"status": "stale"})
            elif previous is not None:
                same = _content_hash(_dump(candidate), ["fetchedAt"]) == _content_hash(
                    _dump(previous), ["fetchedAt"]
                )
                if same and not _older_than(previous.fetchedAt, now, HEARTBEAT):
                    continue

            RegionFile.model_validate(_dump(candidate))
            _write_json(regions_dir / f"{region_id}.json", _dump(candidate))
            current[region_id] = candidate
            report.written.append(f"v1/regions/{region_id}.json")

    _publish_index(v1, current, now_iso, now, report)
    nojekyll = site / ".nojekyll"
    if not nojekyll.exists():
        nojekyll.write_text("", encoding="utf-8")
    return report


def _publish_index(
    v1: Path, regions: dict[str, RegionFile], now_iso: str, now: datetime, report: PublishReport
) -> None:
    order = {rid: i for i, rid in enumerate(REGION_ORDER)}
    entries = [
        RegionIndexEntry(
            id=r.id,
            name=REGION_NAMES[r.id],
            provider=r.provider,
            groups=r.groups(),
            updatedAt=r.updatedAt,
            status=r.status,
        )
        for r in sorted(regions.values(), key=lambda r: order.get(r.id, len(order)))
        if r.id in REGION_NAMES
    ]
    if not entries:
        log.error("no regions with data, index not written")
        return
    index = IndexFile(generatedAt=now_iso, regions=entries)
    path = v1 / "index.json"
    if path.exists():
        try:
            old = IndexFile.model_validate_json(path.read_text(encoding="utf-8"))
            if _content_hash(_dump(old), ["generatedAt"]) == _content_hash(
                _dump(index), ["generatedAt"]
            ) and not _older_than(old.generatedAt, now, HEARTBEAT):
                return
        except (ValidationError, ValueError):
            pass
    _write_json(path, _dump(index))
    report.written.append("v1/index.json")
