"""Khmelnytskoblenergo (Khmelnytskyi region): schedule published as page text.

    "... 07 листопада на Хмельниччині будуть застосовані графіки погодинних
     відключень (ГПВ): Підчерга 1.1 – 00:00-04:00; 09:00-13:00. Підчерга 1.2 – ..."
"""

from __future__ import annotations

import re
from datetime import date

from common.http import NotPublished, ProviderError, get
from common.intervals import build_intervals
from common.models import DaySchedule, RegionSchedule, group_sort_key
from common.text import find_day_month, html_to_text, parse_ranges
from common.timeutil import today_kyiv

_GROUP = re.compile(r"Підчерга\s+(\d+\.\d+)\s*-?\s*(.*?)(?=Підчерга\s+\d+\.\d+|$)", re.S)
_END = re.compile(r"Перевірити чергу|Файли співставлення")


class Khmelnytskoblenergo:
    id = "khmelnytskoblenergo"
    region_ids = ["khmelnytskyi"]
    source_url = "https://hoe.com.ua/page/pogodinni-vidkljuchennja"

    def fetch(self) -> list[RegionSchedule]:
        return [parse(get(self.source_url, accept="text/html").text, today_kyiv())]


def parse(raw: str, today: date) -> RegionSchedule:
    text = html_to_text(raw)
    starts = [m.start() for m in re.finditer(r"Підчерга\s+1\.1\b", text)]
    if not starts:
        raise NotPublished("no schedule text on the page")
    days: dict[str, dict] = {}
    for start in starts:
        day = find_day_month(text[max(0, start - 400):start], today)
        if day is None:
            raise ProviderError("schedule block without a date")
        if day < today or day.isoformat() in days:
            continue
        block = text[start:]
        end = _END.search(block)
        block = block[: end.start()] if end else block[:3000]
        groups = {}
        for name, body in _GROUP.findall(block):
            try:
                groups[name] = build_intervals(parse_ranges(body))
            except ValueError as error:
                raise ProviderError(f"group {name}: {error}") from error
        if groups:
            days[day.isoformat()] = groups
    if not days:
        raise NotPublished("no schedule for today or later")
    return RegionSchedule(
        region_id="khmelnytskyi",
        days=[
            DaySchedule(date=d, groups={g: days[d][g] for g in sorted(days[d], key=group_sort_key)})
            for d in sorted(days)
        ],
    )
