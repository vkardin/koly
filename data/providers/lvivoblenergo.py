"""Lvivoblenergo (Lviv region): menu API behind poweron.loe.lviv.ua.

Every published schedule is a menu item whose `rawHtml` reads:
    "Графік погодинних відключень на 30.10.2025"
    "Інформація станом на 08:24 30.10.2025"
    "Група 1.1. Електроенергії немає з 14:00 до 17:30, з 19:00 до 21:00."
    "Група 2.1. Електроенергія є."
The newest item (highest id) for a date wins. The response is about 2 MB, so
this provider is fetched at most every 30 minutes.
"""

from __future__ import annotations

import re
from datetime import date, datetime, timedelta

from common.http import ProviderError, get
from common.intervals import Span, build_intervals
from common.models import DaySchedule, RegionSchedule, group_sort_key
from common.text import html_to_text, minutes
from common.timeutil import KYIV, iso_kyiv, today_kyiv

API = "https://api.loe.lviv.ua/api/menus?page=1&type=photo-grafic"
_DAY = re.compile(r"Графік погодинних відключень на (\d{2})\.(\d{2})\.(\d{4})")
_AS_OF = re.compile(r"Інформація станом на (\d{1,2}:\d{2}) (\d{2}\.\d{2}\.\d{4})")
_LINE = re.compile(r"Група (\d+\.\d+)\.\s*(.*?)(?=Група \d+\.\d+\.|$)", re.S)
_RANGE = re.compile(r"з (\d{1,2}):(\d{2}) до (\d{1,2}):(\d{2})")


class Lvivoblenergo:
    id = "lvivoblenergo"
    region_ids = ["lviv"]
    source_url = "https://poweron.loe.lviv.ua/"
    min_interval = timedelta(minutes=30)

    def fetch(self) -> list[RegionSchedule]:
        try:
            payload = get(API, accept="application/ld+json").json()
        except ValueError as error:
            raise ProviderError("invalid JSON") from error
        return [parse(payload, today_kyiv())]


def _items(node, out: list) -> list:
    if isinstance(node, dict):
        if isinstance(node.get("rawHtml"), str) and "Група" in node["rawHtml"]:
            out.append(node)
        for value in node.values():
            _items(value, out)
    elif isinstance(node, list):
        for value in node:
            _items(value, out)
    return out


def _parse_item(raw_html: str) -> tuple[date, dict, str | None]:
    text = html_to_text(raw_html)
    day_match = _DAY.search(text)
    if not day_match:
        raise ProviderError("schedule item without a date")
    d, m, y = (int(x) for x in day_match.groups())
    groups = {}
    for name, body in _LINE.findall(text):
        body = body.strip()
        spans: list[Span] = []
        if body.startswith("Електроенергія є"):
            pass
        elif body.startswith("Електроенергії немає"):
            for h1, m1, h2, m2 in _RANGE.findall(body):
                start, end = minutes(h1, m1), minutes(h2, m2, end=True)
                if end > start:
                    spans.append((start, end, "off"))
        else:
            raise ProviderError(f"group {name}: unrecognised text {body[:60]!r}")
        try:
            groups[name] = build_intervals(spans)
        except ValueError as error:
            raise ProviderError(f"group {name}: {error}") from error
    as_of = _AS_OF.search(text)
    stamp = None
    if as_of:
        moment = datetime.strptime(f"{as_of.group(2)} {as_of.group(1)}", "%d.%m.%Y %H:%M")
        stamp = iso_kyiv(moment.replace(tzinfo=KYIV))
    return date(y, m, d), groups, stamp


def parse(payload: dict, today: date) -> RegionSchedule:
    items = sorted(_items(payload, []), key=lambda item: item.get("id", 0), reverse=True)
    if not items:
        raise ProviderError("no schedule items in payload")
    latest: dict[date, tuple[dict, str | None]] = {}
    newest_groups: list[str] = []
    for item in items:
        day, groups, stamp = _parse_item(item["rawHtml"])
        if not newest_groups and groups:
            newest_groups = sorted(groups, key=group_sort_key)
        if day >= today and day not in latest and groups:
            latest[day] = (groups, stamp)
    if not newest_groups:
        raise ProviderError("no groups found")

    if not latest:
        # Nothing published for today: schedules are not applied in the region.
        return RegionSchedule(
            region_id="lviv",
            days=[DaySchedule(date=today.isoformat(), groups={g: [] for g in newest_groups})],
            no_schedules=True,
        )
    stamps = [s for _, s in latest.values() if s]
    return RegionSchedule(
        region_id="lviv",
        updated_at=max(stamps) if stamps else None,
        days=[
            DaySchedule(date=d.isoformat(), groups={g: latest[d][0][g] for g in sorted(latest[d][0], key=group_sort_key)})
            for d in sorted(latest)
        ],
    )
