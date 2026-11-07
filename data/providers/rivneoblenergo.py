"""Rivneoblenergo (Rivne region): HTML table, one row per date, one cell per sub-queue.

A cell lists outage hours ("08:00 - 12:00", several per cell) or says
"Очікується" while the schedule for that date is not published yet.
"""

from __future__ import annotations

import re
from datetime import date, datetime

from common.http import NotPublished, ProviderError, get
from common.intervals import build_intervals
from common.models import DaySchedule, RegionSchedule
from common.text import html_to_text, parse_ranges
from common.timeutil import KYIV, iso_kyiv, today_kyiv

_ROW = re.compile(r"<tr[^>]*>(.*?)</tr>", re.S | re.I)
_CELL = re.compile(r"<td[^>]*>(.*?)</td>", re.S | re.I)
_DATE = re.compile(r"^(\d{2})\.(\d{2})\.(\d{4})$")
_UPDATED = re.compile(r"Оновлено:\s*(\d{2}\.\d{2}\.\d{4})\s+(\d{1,2}:\d{2})")
_WAITING = re.compile(r"очікується", re.I)
_NO_OUTAGES = re.compile(r"не (?:застосовується|передбачено|плануються)|відсутні|без відключень", re.I)


class Rivneoblenergo:
    id = "rivneoblenergo"
    region_ids = ["rivne"]
    source_url = "https://www.roe.vsei.ua/disconnections"

    def fetch(self) -> list[RegionSchedule]:
        return [parse(get(self.source_url, accept="text/html").text, today_kyiv())]


def parse(raw: str, today: date) -> RegionSchedule:
    start = raw.find('id="fetched-data-container"')
    if start < 0:
        raise ProviderError("schedule table not found")
    section = raw[start:]
    section = section[: section.find("</table>")]
    rows = [[html_to_text(c).strip() for c in _CELL.findall(r)] for r in _ROW.findall(section)]

    groups_row = next((r for r in rows if r and r[0] == "Підчерга"), None)
    if not groups_row:
        raise ProviderError("sub-queue header not found")
    names = groups_row[1:]
    if not names or not all(re.fullmatch(r"\d+\.\d+", n) for n in names):
        raise ProviderError(f"unexpected sub-queue header {names}")

    days: list[DaySchedule] = []
    waiting = False
    for row in rows:
        match = _DATE.match(row[0]) if row else None
        if not match:
            continue
        day = date(int(match.group(3)), int(match.group(2)), int(match.group(1)))
        cells = row[1:]
        if len(cells) != len(names):
            raise ProviderError(f"{day}: expected {len(names)} cells, got {len(cells)}")
        if any(_WAITING.search(c) for c in cells):
            waiting = True
            continue
        if day < today:
            continue
        groups = {}
        for name, cell in zip(names, cells):
            try:
                spans = parse_ranges(cell)
            except ValueError as error:
                raise ProviderError(f"{day} {name}: {error}") from error
            if not spans and cell and not _NO_OUTAGES.search(cell):
                raise ProviderError(f"{day} {name}: unrecognised cell {cell!r}")
            groups[name] = build_intervals(spans)
        days.append(DaySchedule(date=day.isoformat(), groups=groups))

    if not days:
        raise NotPublished("schedule is expected but not published yet" if waiting else "no dates")
    updated = _UPDATED.search(html_to_text(raw[start:]))
    updated_at = None
    if updated:
        moment = datetime.strptime(f"{updated.group(1)} {updated.group(2)}", "%d.%m.%Y %H:%M")
        updated_at = iso_kyiv(moment.replace(tzinfo=KYIV))
    return RegionSchedule(region_id="rivne", updated_at=updated_at, days=sorted(days, key=lambda d: d.date))
