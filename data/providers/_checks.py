"""Reads a region from more than one source and records whether they agree.

The primary provider decides what is published. Every other source of the same region
(another parser of the operator's site, an open mirror) is read too and compared day by
day, group by group. When they agree the region file says so; when they differ, the other
source's version of the differing days is published next to the main one, with its name and
link, so people can see both. If the primary source fails, the first other source that
works is published instead, so a parsing error at one source does not leave a region empty.

Other sources that read the operator's own site are throttled (`min_interval`), so the
operator does not get more requests than before; mirrors live on GitHub and are cheap.
"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Callable

from common.http import NotPublished, ProviderError
from common.models import DaySchedule, RegionFile, RegionSchedule, SourceCheck, SourceKind
from common.timeutil import iso_kyiv, now_kyiv, today_kyiv

from . import _mirror

log = logging.getLogger("koly.checks")


@dataclass(frozen=True)
class Source:
    id: str
    kind: SourceKind
    name: str
    url: str
    read: Callable[[str], RegionSchedule]
    # Sources that hit the operator's own site are read at most this often.
    min_interval: timedelta | None = None


def mirror(m: _mirror.Mirror, source_id: str) -> Source:
    name = m.page.rstrip("/").rsplit("/", 1)[-1]
    return Source(source_id, "mirror", name, m.page,
                  lambda region_id: _mirror.fetch(m, region_id, today_kyiv(), now_kyiv()))


def library(operator_id: str, url: str) -> Source:
    from .uamonitor import _library  # imported late: the library is optional for most tests

    return Source(f"uem:{operator_id}", "library", "ukraine-energy-monitor", url,
                  lambda region_id: asyncio.run(_library(operator_id, region_id, today_kyiv())),
                  min_interval=timedelta(minutes=30))


def _intervals(day: DaySchedule, group: str) -> list[tuple[str, str, str]]:
    # Some sources end the day at 23:59, others at 24:00: the same moment.
    return [(i.from_, "24:00" if i.to == "23:59" else i.to, i.type) for i in day.groups.get(group, [])]


def compare(main: RegionSchedule, other: RegionSchedule, today: str) -> tuple[str, list[DaySchedule]]:
    """'same', 'different' or 'none' (nothing to compare) plus the other source's differing days (dates from today on).

    Only groups both sources know are compared: sources sometimes name or list groups
    differently, and a group one of them lacks is not a disagreement about hours.
    """
    if main.no_schedules != other.no_schedules:
        return "different", [d for d in other.days if d.date >= today]
    mine = {d.date: d for d in main.days if d.date >= today}
    differing: list[DaySchedule] = []
    compared = 0
    for day in other.days:
        if day.date < today:
            continue
        own = mine.get(day.date)
        if own is None:
            if not other.no_schedules and any(day.groups.values()):
                differing.append(day)  # only the other source has this day yet
            continue
        shared = [g for g in day.groups if g in own.groups]
        if not shared:
            continue
        compared += 1
        if any(_intervals(own, g) != _intervals(day, g) for g in shared):
            differing.append(DaySchedule(date=day.date, groups={g: day.groups[g] for g in shared}))
    if differing:
        return "different", differing
    # Nothing in common to compare (e.g. the other source has only an older day): no verdict.
    return ("same" if compared or main.no_schedules else "none"), []


def _previous(site: Path | None, region_id: str) -> RegionFile | None:
    if site is None:
        return None
    path = site / "v1" / "regions" / f"{region_id}.json"
    try:
        return RegionFile.model_validate_json(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


class Checked:
    """`provider` (one region) cross-checked against `sources`, in order of trust."""

    def __init__(self, provider, sources: list[Source]) -> None:
        self.provider = provider
        self.sources = sources
        self.id = provider.id
        self.region_ids = list(provider.region_ids)
        self.source_url = provider.source_url
        self.site: Path | None = None
        if hasattr(provider, "min_interval"):
            self.min_interval = provider.min_interval

    def bind_site(self, site: Path) -> None:
        self.site = site

    def _read_all(self, region_id: str, now: datetime) -> list[tuple[Source, RegionSchedule | None, str | None]]:
        previous = _previous(self.site, region_id)
        last = {c.source: c for c in previous.checks} if previous else {}
        out: list[tuple[Source, RegionSchedule | None, str | None]] = []
        for source in self.sources:
            prev = last.get(source.id)
            if (source.min_interval and prev is not None
                    and now - datetime.fromisoformat(prev.checkedAt) < source.min_interval):
                out.append((source, None, "reuse"))
                continue
            try:
                out.append((source, source.read(region_id), None))
            except NotPublished as error:
                out.append((source, None, f"not published: {error}"))
            except Exception as error:  # someone else's data: anything can go wrong
                out.append((source, None, f"{type(error).__name__}: {error}"[:200]))
        return out

    def fetch(self) -> list[RegionSchedule]:
        region_id = self.region_ids[0]
        now = now_kyiv()
        primary_error: Exception | None = None
        try:
            main = self.provider.fetch()[0]
        except Exception as error:
            primary_error = error
            main = None
        readings = self._read_all(region_id, now)
        if main is None:
            # The primary source failed or has nothing yet: publish the first other source that has data.
            for i, (source, schedule, _) in enumerate(readings):
                if schedule is not None:
                    log.warning("%s: primary failed (%s), using %s", region_id, primary_error, source.id)
                    main = schedule
                    readings = readings[:i] + readings[i + 1:]
                    break
            else:
                if isinstance(primary_error, NotPublished):
                    raise primary_error
                raise ProviderError(str(primary_error)[:400]) from primary_error
        previous = _previous(self.site, region_id)
        last = {c.source: c for c in previous.checks} if previous else {}
        today = today_kyiv(now).isoformat()
        checks: list[SourceCheck] = []
        for source, schedule, note in readings:
            if note == "reuse":
                prev = last[source.id]
                # Keep the earlier reading only while it still says something about today on.
                checks.append(prev.model_copy(update={"days": [d for d in prev.days if d.date >= today]}))
                continue
            base = dict(source=source.id, kind=source.kind, name=source.name, url=source.url,
                        checkedAt=iso_kyiv(now))
            if schedule is None:
                if note and note.startswith("not published"):
                    continue  # nothing to compare yet: not a failure of the source
                checks.append(SourceCheck(**base, result="failed"))
                continue
            result, days = compare(main, schedule, today)
            if result == "none":
                continue
            if (result == "different" and schedule.updated_at and main.updated_at
                    and datetime.fromisoformat(schedule.updated_at) < datetime.fromisoformat(main.updated_at)):
                # The other source shows an earlier version of the schedule: worth noting, not showing.
                result, days = "older", []
            checks.append(SourceCheck(**base, updatedAt=schedule.updated_at, result=result, days=days))
            if result == "different":
                log.warning("%s: %s disagrees on %s", region_id, source.id, [d.date for d in days] or "schedules")
        return [main.model_copy(update={"checks": checks})]
