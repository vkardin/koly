"""Zhytomyroblenergo (Zhytomyr region): HTML table with half-hour cells.

The table header holds one cell per date (colspan 48), then a row of half-hour
labels; every sub-queue row has 48 cells per date. Cells painted #ff3333 mean
no power, white means power is on. The page is windows-1251.
"""

from __future__ import annotations

import re
from datetime import date, datetime

from common.http import NotPublished, ProviderError, get
from common.intervals import Span, build_intervals
from common.models import DaySchedule, RegionSchedule, group_sort_key
from common.timeutil import KYIV, iso_kyiv, today_kyiv

_ROW = re.compile(r"<tr[^>]*>(.*?)</tr>", re.S | re.I)
_CELL = re.compile(r"<td([^>]*)>(.*?)</td>", re.S | re.I)
_DATE = re.compile(r"(\d{2})\.(\d{2})\.(\d{4})")
_UPDATED = re.compile(r"Дата оновлення інформації\s*-\s*(\d{1,2}:\d{2})\s+(\d{2}\.\d{2}\.\d{4})")
_OFF = {"#ff3333"}
_ON = {"#ffffff", "white"}


class Zhytomyroblenergo:
    id = "zhytomyroblenergo"
    region_ids = ["zhytomyr"]
    source_url = "https://www.ztoe.com.ua/unhooking-search.php"

    def fetch(self) -> list[RegionSchedule]:
        response = get(self.source_url, accept="text/html")
        return [parse(response.content.decode("cp1251", errors="replace"), today_kyiv())]


def _clean(cell: str) -> str:
    return re.sub(r"<[^>]+>|&nbsp;|\s+", "", cell)


def _background(attrs: str) -> str | None:
    match = re.search(r"background(?:-color)?:\s*([^;\"]+)", attrs, re.I)
    return match.group(1).strip().lower() if match else None


def parse(raw: str, today: date) -> RegionSchedule:
    start = raw.find("Графіки погодинних відключень")
    if start < 0:
        raise ProviderError("schedule section not found")
    section = raw[start:]
    updated = _UPDATED.search(section)
    table_end = section.find("</table>")
    rows = _ROW.findall(section[: table_end if table_end > 0 else None])
    if len(rows) < 3:
        raise NotPublished("schedule table is empty")

    dates = [
        date(int(y), int(m), int(d))
        for attrs, cell in _CELL.findall(rows[0])
        if 'colspan="48"' in attrs
        for d, m, y in [_DATE.search(_clean(cell)).groups()]
    ]
    if not dates:
        raise ProviderError("no dates in table header")

    per_day: list[dict] = [{} for _ in dates]
    for row in rows[2:]:
        cells = _CELL.findall(row)
        names = [i for i, (_, c) in enumerate(cells) if re.fullmatch(r"\d+\.\d+", _clean(c))]
        if not names:
            continue
        name = _clean(cells[names[0]][1])
        slots = [_background(a) for a, c in cells[names[0] + 1:] if _background(a) is not None]
        if len(slots) != 48 * len(dates):
            raise ProviderError(f"group {name}: expected {48 * len(dates)} cells, got {len(slots)}")
        for index in range(len(dates)):
            spans: list[Span] = []
            for slot, color in enumerate(slots[index * 48:(index + 1) * 48]):
                if color in _OFF:
                    spans.append((slot * 30, slot * 30 + 30, "off"))
                elif color not in _ON:
                    raise ProviderError(f"group {name}: unknown cell colour {color}")
            per_day[index][name] = build_intervals(spans)

    days = [
        DaySchedule(date=d.isoformat(), groups={g: groups[g] for g in sorted(groups, key=group_sort_key)})
        for d, groups in zip(dates, per_day)
        if groups and d >= today
    ]
    if not days:
        raise NotPublished("no schedule for today or later")
    updated_at = None
    if updated:
        moment = datetime.strptime(f"{updated.group(2)} {updated.group(1)}", "%d.%m.%Y %H:%M")
        updated_at = iso_kyiv(moment.replace(tzinfo=KYIV))
    return RegionSchedule(region_id="zhytomyr", updated_at=updated_at, days=days)
