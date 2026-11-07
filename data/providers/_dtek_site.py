"""Parser for DTEK network "shutdowns" pages, which embed the schedule as JSON.

The page contains a script with `DisconSchedule.fact = {...}`:
    {"data": {"<unix midnight>": {"GPV1.1": {"1": "yes", ..., "24": "no"}, ...}},
     "update": "07.11.2026 15:39", "today": <unix midnight>}

Hour keys "1".."24" cover [h-1:00, h:00). Values:
    yes      power on
    no       no power for the whole hour
    first    no power in the first 30 minutes
    second   no power in the second 30 minutes
    maybe    possible outage for the whole hour
    mfirst   possible outage in the first 30 minutes
    msecond  possible outage in the second 30 minutes
"""

from __future__ import annotations

import json
import re
from datetime import datetime

from common.http import ProviderError, get
from common.intervals import Span, build_intervals
from common.models import DaySchedule, RegionSchedule, group_sort_key
from common.timeutil import KYIV, iso_kyiv

_FACT = re.compile(r"DisconSchedule\.fact\s*=\s*")
_GROUP = re.compile(r"^GPV(.+)$")

_HALVES: dict[str, list[tuple[int, int, str]]] = {
    "yes": [],
    "no": [(0, 60, "off")],
    "first": [(0, 30, "off")],
    "second": [(30, 60, "off")],
    "maybe": [(0, 60, "maybe")],
    "mfirst": [(0, 30, "maybe")],
    "msecond": [(30, 60, "maybe")],
}


def fetch_page(url: str) -> str:
    html = get(url, accept="text/html").text
    if "_Incapsula_Resource" in html and "DisconSchedule" not in html:
        raise ProviderError(f"{url} is behind a bot challenge")
    return html


def extract_fact(html: str) -> dict:
    match = _FACT.search(html)
    if not match:
        raise ProviderError("DisconSchedule.fact not found in page")
    try:
        fact, _ = json.JSONDecoder().raw_decode(html, match.end())
    except ValueError as error:
        raise ProviderError("DisconSchedule.fact is not valid JSON") from error
    if not isinstance(fact, dict) or not isinstance(fact.get("data"), dict):
        raise ProviderError("DisconSchedule.fact has no data")
    return fact


def parse(html: str, region_id: str) -> RegionSchedule:
    fact = extract_fact(html)
    days: list[DaySchedule] = []
    for stamp, groups in sorted(fact["data"].items(), key=lambda item: int(item[0])):
        date = datetime.fromtimestamp(int(stamp), KYIV).date().isoformat()
        parsed: dict = {}
        for raw_name, hours in groups.items():
            name_match = _GROUP.match(raw_name)
            if not name_match:
                continue
            spans: list[Span] = []
            for hour, value in hours.items():
                h = int(hour)
                if not 1 <= h <= 24:
                    raise ProviderError(f"hour out of range: {hour}")
                if value not in _HALVES:
                    raise ProviderError(f"unknown value {value!r} for {raw_name} hour {hour}")
                base = (h - 1) * 60
                spans.extend((base + s, base + e, t) for s, e, t in _HALVES[value])
            try:
                parsed[name_match.group(1)] = build_intervals(spans)
            except ValueError as error:
                raise ProviderError(f"{raw_name} {date}: {error}") from error
        if parsed:
            days.append(
                DaySchedule(
                    date=date,
                    groups={g: parsed[g] for g in sorted(parsed, key=group_sort_key)},
                )
            )
    if not days:
        raise ProviderError("no groups in schedule")
    return RegionSchedule(region_id=region_id, updated_at=_updated(fact), days=days)


def _updated(fact: dict) -> str:
    raw = fact.get("update")
    if not raw:
        raise ProviderError("schedule has no update time")
    try:
        moment = datetime.strptime(raw.strip(), "%d.%m.%Y %H:%M").replace(tzinfo=KYIV)
    except ValueError as error:
        raise ProviderError(f"unexpected update time {raw!r}") from error
    return iso_kyiv(moment)
