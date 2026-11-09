"""Parser for the public YASNO planned-outages API (used by DTEK networks).

Response shape (one object per group):
    {"1.1": {"today": {"slots": [{"start": 0, "end": 420, "type": "Definite"}, ...],
                       "date": "2026-11-07T00:00:00+02:00", "status": "ScheduleApplies"},
             "tomorrow": {...}, "updatedOn": "2026-11-07T11:45:01+00:00"}, ...}

`start`/`end` are minutes from local midnight. `Definite` means no power,
`NotPlanned` means power is on.

`EmergencyShutdowns` with no outage slots means the planned schedule is
suspended, not that power is on all day. Such days are never published as
empty schedules: they are left out, and if nothing else is published the
region gets `no_schedules`. Providers can then fill those days from the
DTEK site (see `fill_from_site`).
"""

from __future__ import annotations

import logging
from datetime import datetime

from common.http import ProviderError, get
from common.intervals import Span, build_intervals
from common.models import DaySchedule, Emergency, RegionSchedule, group_sort_key
from common.timeutil import iso_kyiv

API = "https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/{region}/dsos/{dso}/planned-outages"

PUBLISHED = {"ScheduleApplies"}
EMERGENCY = {"EmergencyShutdowns"}
NOT_PUBLISHED = {"WaitingForSchedule"}

log = logging.getLogger(__name__)


def api_url(region: int, dso: int) -> str:
    return API.format(region=region, dso=dso)


def fetch_payload(url: str) -> dict:
    response = get(url, accept="application/json")
    try:
        return response.json()
    except ValueError as error:
        raise ProviderError(f"invalid JSON from {url}") from error


def _slot_type(raw: str) -> str | None:
    if raw == "NotPlanned":
        return None
    if raw == "Definite":
        return "off"
    if raw.startswith("Possible"):
        return "maybe"
    raise ProviderError(f"unknown slot type {raw!r}")


def parse(payload: dict, region_id: str) -> RegionSchedule:
    return parse_with_gaps(payload, region_id)[0]


def parse_with_gaps(payload: dict, region_id: str) -> tuple[RegionSchedule, list[str]]:
    """Returns the schedule and the emergency dates that have no outage slots."""
    if not isinstance(payload, dict) or not payload:
        raise ProviderError("empty payload")

    by_date: dict[str, dict] = {}
    updated: list[datetime] = []
    emergency = False
    emergency_dates: set[str] = set()

    for group, entry in payload.items():
        if not isinstance(entry, dict) or "today" not in entry:
            raise ProviderError(f"unexpected entry for group {group!r}")
        if entry.get("updatedOn"):
            updated.append(datetime.fromisoformat(entry["updatedOn"]))
        for key in ("today", "tomorrow"):
            day = entry.get(key)
            if not day:
                continue
            status = day.get("status")
            if status in EMERGENCY:
                emergency = True
            if status not in PUBLISHED and status not in EMERGENCY:
                continue
            date = datetime.fromisoformat(day["date"]).date().isoformat()
            if status in EMERGENCY:
                emergency_dates.add(date)
            spans: list[Span] = []
            for slot in day.get("slots", []):
                kind = _slot_type(slot["type"])
                if kind:
                    spans.append((int(slot["start"]), int(slot["end"]), kind))
            try:
                by_date.setdefault(date, {})[group] = build_intervals(spans)
            except ValueError as error:
                raise ProviderError(f"group {group} {date}: {error}") from error

    if not updated:
        raise ProviderError("payload has no updatedOn")

    # An emergency day where no group has an outage slot carries no schedule.
    gaps = sorted(
        d for d in emergency_dates if d in by_date and not any(by_date[d].values())
    )
    groups_seen = sorted({g for day in by_date.values() for g in day}, key=group_sort_key)
    for date in gaps:
        del by_date[date]
    if not by_date and gaps:
        return (
            RegionSchedule(
                region_id=region_id,
                updated_at=iso_kyiv(max(updated)),
                days=[DaySchedule(date=d, groups={g: [] for g in groups_seen}) for d in gaps],
                emergency=Emergency(active=True),
                no_schedules=True,
            ),
            gaps,
        )
    if not by_date:
        raise ProviderError("no published days in payload")

    days = [
        DaySchedule(
            date=date,
            groups={g: by_date[date][g] for g in sorted(by_date[date], key=group_sort_key)},
        )
        for date in sorted(by_date)
    ]
    return (
        RegionSchedule(
            region_id=region_id,
            updated_at=iso_kyiv(max(updated)),
            days=days,
            emergency=Emergency(active=emergency),
        ),
        gaps,
    )


def fill_from_site(schedule: RegionSchedule, gaps: list[str], fetch_site) -> RegionSchedule:
    """Fills emergency days without slots from the DTEK site schedule, if it can be read.

    `fetch_site` returns the site's RegionSchedule or raises ProviderError (for
    example when the page is behind a bot challenge); then the YASNO result stands.
    """
    if not gaps:
        return schedule
    try:
        site = fetch_site()
    except ProviderError as error:
        log.info("%s: site fallback unavailable: %s", schedule.region_id, error)
        return schedule
    site_days = {d.date: d for d in site.days if d.date in gaps}
    if not site_days:
        return schedule
    # A no_schedules result only holds placeholder days for the gaps.
    kept = [] if schedule.no_schedules else list(schedule.days)
    days = sorted(kept + list(site_days.values()), key=lambda d: d.date)
    stamps = [s for s in (schedule.updated_at, site.updated_at) if s]
    return schedule.model_copy(
        update={"days": days, "no_schedules": False, "updated_at": max(stamps) if stamps else None}
    )
