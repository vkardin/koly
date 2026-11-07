"""Mykolaivoblenergo (Mykolaiv region): JSON API behind off.energy.mk.ua.

- /api/outage-queue/by-type/3 lists sub-queues: [{"id": 14, "name": "1.1"}, ...]
- /api/schedule/time-series lists 48 half-hour slots: [{"id": 1, "start": "00:00:00"}, ...]
- /api/v2/schedule/active lists schedules: [{"from": UTC, "to": UTC,
  "series": [{"time_series_id": 1, "outage_queue_id": 21, "type": "OFF", "updated_at": UTC}]}]
`OFF` means no power, `PROBABLY_OFF` a possible outage.
"""

from __future__ import annotations

from datetime import datetime

from common.http import NotPublished, ProviderError, get
from common.intervals import Span, build_intervals
from common.models import DaySchedule, RegionSchedule, group_sort_key
from common.timeutil import KYIV, iso_kyiv

API = "https://off.energy.mk.ua/api"
SUBQUEUE_TYPE = 3
_TYPES = {"OFF": "off", "PROBABLY_OFF": "maybe"}


class Mykolaivoblenergo:
    id = "mykolaivoblenergo"
    region_ids = ["mykolaiv"]
    source_url = "https://off.energy.mk.ua/"

    def fetch(self) -> list[RegionSchedule]:
        def load(path: str):
            try:
                return get(f"{API}/{path}", accept="application/json").json()
            except ValueError as error:
                raise ProviderError(f"invalid JSON from {path}") from error

        return [
            parse(
                load("v2/schedule/active"),
                load(f"outage-queue/by-type/{SUBQUEUE_TYPE}"),
                load("schedule/time-series"),
            )
        ]


def _utc(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def parse(schedules: list, queues: list, series: list) -> RegionSchedule:
    names = {q["id"]: q["name"] for q in queues if not q.get("deleted")}
    if not names:
        raise ProviderError("no sub-queues")
    slots: dict[int, int] = {}
    for item in series:
        hours, mins = item["start"].split(":")[:2]
        slots[item["id"]] = int(hours) * 60 + int(mins)
    if len(slots) != 48:
        raise ProviderError(f"expected 48 time slots, got {len(slots)}")

    days: dict[str, dict] = {}
    updated: list[datetime] = []
    for schedule in schedules:
        day = _utc(schedule["from"]).astimezone(KYIV).date().isoformat()
        spans: dict[str, list[Span]] = {name: [] for name in names.values()}
        for entry in schedule.get("series", []):
            name = names.get(entry["outage_queue_id"])
            if name is None:
                continue  # other queue types (industry, emergency) share the table
            kind = _TYPES.get(entry["type"])
            if kind is None:
                raise ProviderError(f"unknown slot type {entry['type']!r}")
            start = slots.get(entry["time_series_id"])
            if start is None:
                raise ProviderError(f"unknown time slot {entry['time_series_id']}")
            spans[name].append((start, start + 30, kind))
            if entry.get("updated_at"):
                updated.append(_utc(entry["updated_at"]))
        try:
            groups = {n: build_intervals(s) for n, s in spans.items()}
        except ValueError as error:
            raise ProviderError(f"{day}: {error}") from error
        days[day] = {g: groups[g] for g in sorted(groups, key=group_sort_key)}

    if not days:
        raise NotPublished("no active schedule")
    return RegionSchedule(
        region_id="mykolaiv",
        updated_at=iso_kyiv(max(updated)) if updated else None,
        days=[DaySchedule(date=d, groups=days[d]) for d in sorted(days)],
    )
