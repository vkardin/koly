"""Chernivtsioblenergo (Chernivtsi region): HTML grid with half-hour cells.

The shutdowns page holds one grid per published date: a header with the date
(`<ul><p>16.11.2026</p>`), then one row per group, `<div id="infN">`, with 48
half-hour cells. Cell letters: "з" power on, "в" no power, "мз" maybe powered
(the "grey zone", published as `maybe`). "Графік актуальний станом на <b>HH:MM</b>"
is the time of the last update.
"""

from __future__ import annotations

import re
from datetime import date, datetime

from common.http import NotPublished, ProviderError, get
from common.intervals import Span, build_intervals
from common.models import DaySchedule, RegionSchedule
from common.timeutil import KYIV, iso_kyiv, today_kyiv

_GRID = re.compile(r'<div id="gsv"[^>]*>\s*<ul>\s*<p>\s*(\d{2})\.(\d{2})\.(\d{4})', re.S)
_ROW = re.compile(r'<div id="inf(\d+)"[^>]*>(.*?)</div>', re.S)
_CELL = re.compile(r"<(u|o|s)>\s*([^<]*?)\s*</\1>", re.S)
_UPDATED = re.compile(r"станом на\s*<b>(\d{1,2}:\d{2})</b>")
_TITLE_DATE = re.compile(r'<div id="gsv_t"[^>]*>.*?<b>(\d{2})\.(\d{2})\.(\d{4})</b>', re.S)
_KIND = {"з": None, "в": "off", "мз": "maybe"}


class Chernivtsioblenergo:
    id = "chernivtsioblenergo"
    region_ids = ["chernivtsi"]
    source_url = "https://oblenergo.cv.ua/shutdowns/"

    def fetch(self) -> list[RegionSchedule]:
        response = get(self.source_url, accept="text/html")
        return [parse(response.text, today_kyiv())]


def _day(day: date, block: str) -> DaySchedule:
    groups: dict[str, list] = {}
    for number, row in _ROW.findall(block):
        cells = _CELL.findall(row)
        if len(cells) != 48:
            raise ProviderError(f"group {number}: expected 48 cells, got {len(cells)}")
        spans: list[Span] = []
        for slot, (_, letter) in enumerate(cells):
            letter = letter.lower()
            if letter not in _KIND:
                raise ProviderError(f"group {number}: unknown cell {letter!r}")
            kind = _KIND[letter]
            if kind:
                spans.append((slot * 30, slot * 30 + 30, kind))
        groups[number] = build_intervals(spans)
    if not groups:
        raise ProviderError(f"{day}: no group rows")
    return DaySchedule(date=day.isoformat(), groups={g: groups[g] for g in sorted(groups, key=int)})


def parse(raw: str, today: date) -> RegionSchedule:
    grids = list(_GRID.finditer(raw))
    if not grids:
        if "gsv" in raw:
            raise NotPublished("no schedule grid on the page")
        raise ProviderError("schedule section not found")
    days: list[DaySchedule] = []
    for n, match in enumerate(grids):
        d, m, y = match.groups()
        day = date(int(y), int(m), int(d))
        end = grids[n + 1].start() if n + 1 < len(grids) else len(raw)
        if day >= today:
            days.append(_day(day, raw[match.end():end]))
    if not days:
        raise NotPublished("no schedule for today or later")
    updated_at = None
    updated = _UPDATED.search(raw)
    title = _TITLE_DATE.search(raw)
    if updated and title:
        d, m, y = title.groups()
        moment = datetime.strptime(f"{d}.{m}.{y} {updated.group(1)}", "%d.%m.%Y %H:%M")
        updated_at = iso_kyiv(moment.replace(tzinfo=KYIV))
    return RegionSchedule(region_id="chernivtsi", updated_at=updated_at, days=days)
