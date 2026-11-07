"""Parser for the public YASNO planned-outages API (used by DTEK networks).

Response shape (one object per group):
    {"1.1": {"today": {"slots": [{"start": 0, "end": 420, "type": "Definite"}, ...],
                       "date": "2026-11-07T00:00:00+02:00", "status": "ScheduleApplies"},
             "tomorrow": {...}, "updatedOn": "2026-11-07T11:45:01+00:00"}, ...}

`start`/`end` are minutes from local midnight. `Definite` means no power,
`NotPlanned` means power is on.
"""

from __future__ import annotations

from datetime import datetime

from common.http import ProviderError, get
from common.intervals import Span, build_intervals
from common.models import DaySchedule, Emergency, RegionSchedule, group_sort_key
from common.timeutil import iso_kyiv

API = "https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/{region}/dsos/{dso}/planned-outages"

PUBLISHED = {"ScheduleApplies"}
EMERGENCY = {"EmergencyShutdowns"}
NOT_PUBLISHED = {"WaitingForSchedule"}


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
    if not isinstance(payload, dict) or not payload:
        raise ProviderError("empty payload")

    by_date: dict[str, dict] = {}
    updated: list[datetime] = []
    emergency = False

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
            spans: list[Span] = []
            for slot in day.get("slots", []):
                kind = _slot_type(slot["type"])
                if kind:
                    spans.append((int(slot["start"]), int(slot["end"]), kind))
            try:
                by_date.setdefault(date, {})[group] = build_intervals(spans)
            except ValueError as error:
                raise ProviderError(f"group {group} {date}: {error}") from error

    if not by_date:
        raise ProviderError("no published days in payload")
    if not updated:
        raise ProviderError("payload has no updatedOn")

    days = [
        DaySchedule(
            date=date,
            groups={g: by_date[date][g] for g in sorted(by_date[date], key=group_sort_key)},
        )
        for date in sorted(by_date)
    ]
    return RegionSchedule(
        region_id=region_id,
        updated_at=iso_kyiv(max(updated)),
        days=days,
        emergency=Emergency(active=emergency),
    )
