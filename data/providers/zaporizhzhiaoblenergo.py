"""Zaporizhzhiaoblenergo (Zaporizhzhia region): schedules published as news posts.

Posts are listed newest first, e.g.
    "7 ЛИСТОПАДА ПО ЗАПОРІЗЬКІЙ ОБЛАСТІ ДІЯТИМУТЬ ГПВ ... Години відсутності
     електропостачання по чергам (підчергам) ...: 1.1: 00:00 – 02:00, 06:00 – 11:00 1.2: ..."
    "ОНОВЛЕНО ГПВ НА 5 ЛИСТОПАДА ПО ЗАПОРІЗЬКІЙ ОБЛАСТІ (оновлено 21:25) ..."
The first post for a date is the latest version of that day's schedule.
"""

from __future__ import annotations

import re
from datetime import date

from common.http import NotPublished, ProviderError, get
from common.intervals import build_intervals
from common.models import DaySchedule, RegionSchedule, group_sort_key
from common.text import MONTH_PATTERN, MONTHS_GENITIVE, html_to_text, parse_ranges, resolve_day_month
from common.timeutil import today_kyiv

_POST = re.compile(rf"(?:ОНОВЛЕНО ГПВ НА\s+)?(\d{{1,2}})\s+({MONTH_PATTERN})\s+ПО ЗАПОРІЗЬКІЙ ОБЛАСТІ", re.I)
_GROUP = re.compile(r"(?<![\d.])(\d\.\d):\s*((?:\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}[\s,;]*)+)")


class Zaporizhzhiaoblenergo:
    id = "zaporizhzhiaoblenergo"
    region_ids = ["zaporizhzhia"]
    source_url = "https://www.zoe.com.ua/outage/"

    def fetch(self) -> list[RegionSchedule]:
        return [parse(get(self.source_url, accept="text/html").text, today_kyiv())]


def parse(raw: str, today: date) -> RegionSchedule:
    text = html_to_text(raw)
    posts = list(_POST.finditer(text))
    days: dict[str, dict] = {}
    for index, post in enumerate(posts):
        day = resolve_day_month(int(post.group(1)), MONTHS_GENITIVE[post.group(2).lower()], today)
        key = day.isoformat()
        if day < today or key in days:
            continue
        end = posts[index + 1].start() if index + 1 < len(posts) else len(text)
        body = text[post.end():end]
        hours = body.find("Години відсутності")
        if hours < 0:
            continue  # announcement without hours; an older post may still carry them
        groups = {}
        for name, ranges in _GROUP.findall(body[hours:]):
            if name in groups:
                break  # the next paragraph (e.g. a forecast) starts over
            try:
                groups[name] = build_intervals(parse_ranges(ranges))
            except ValueError as error:
                raise ProviderError(f"{key} group {name}: {error}") from error
        if groups:
            days[key] = groups
    if not days:
        raise NotPublished("no schedule for today or later")
    return RegionSchedule(
        region_id="zaporizhzhia",
        days=[
            DaySchedule(date=d, groups={g: days[d][g] for g in sorted(days[d], key=group_sort_key)})
            for d in sorted(days)
        ],
    )
