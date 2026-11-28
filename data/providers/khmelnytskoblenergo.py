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
from common.text import MONTH_PATTERN, MONTHS_GENITIVE, html_to_text, parse_ranges, resolve_day_month
from common.timeutil import today_kyiv

_ENTRY = re.compile(
    r"Підчерга\s+(\d+\.\d+)\s*-?\s*((?:\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}[\s;,.]*)*)"
)
_WORDS = re.compile(r"[A-Za-zА-Яа-яІіЇїЄєҐґ'’]{3,}")
# Words that can stand where a group has no hours; they are not the heading of a new block.
_NO_HOURS = re.compile(r"відсутн\w*|немає|без\s+відключень|не\s+застосовується", re.I)
_ADDITIONAL = re.compile(r"Додатков", re.I)
_DATE = re.compile(rf"(\d{{1,2}})\s+({MONTH_PATTERN})", re.I)


class Khmelnytskoblenergo:
    id = "khmelnytskoblenergo"
    region_ids = ["khmelnytskyi"]
    source_url = "https://hoe.com.ua/page/pogodinni-vidkljuchennja"

    def fetch(self) -> list[RegionSchedule]:
        return [parse(get(self.source_url, accept="text/html").text, today_kyiv())]


def _last_date(text: str, today: date) -> date | None:
    found = None
    for match in _DATE.finditer(text):
        found = resolve_day_month(int(match.group(1)), MONTHS_GENITIVE[match.group(2).lower()], today)
    return found


def _sections(text: str) -> list[tuple[str, int, dict[str, list]]]:
    """Blocks of "Підчерга N.M - hours" lines: (heading, position, {group: spans}).

    A block ends where words that are not part of a line appear between two lines: the heading of
    the next block, such as "(оновлено з 20:00) Додаткові години відсутності електроенергії:".
    """
    sections: list[tuple[str, int, dict[str, list]]] = []
    previous_end = 0
    for match in _ENTRY.finditer(text):
        gap = _NO_HOURS.sub(" ", text[previous_end:match.start()])
        if not sections or _WORDS.search(gap):
            sections.append((text[max(0, match.start() - 400 if not sections else previous_end):match.start()],
                             match.start(), {}))
        name, hours = match.group(1), match.group(2)
        try:
            sections[-1][2][name] = parse_ranges(hours)
        except ValueError as error:
            raise ProviderError(f"group {name}: {error}") from error
        previous_end = match.end()
    return sections


def parse(raw: str, today: date) -> RegionSchedule:
    text = html_to_text(raw)
    sections = _sections(text)
    if not sections:
        raise NotPublished("no schedule text on the page")
    base: dict[str, dict[str, list]] = {}
    extra: dict[str, list[dict[str, list]]] = {}
    for heading, position, groups in sections:
        # The date is in the block's own heading or, for an update block, in the title above it.
        day = _last_date(heading, today) or _last_date(text[:position], today)
        if day is None:
            raise ProviderError("schedule block without a date")
        if day < today:
            continue
        key = day.isoformat()
        if _ADDITIONAL.search(heading):
            # "Додаткові години": hours added to the day's schedule, not a replacement of it.
            extra.setdefault(key, []).append(groups)
        elif key not in base:  # the newest block comes first
            base[key] = groups
    if not base and not extra:
        raise NotPublished("no schedule for today or later")
    days = []
    for key in sorted(set(base) | set(extra)):
        if key not in base:
            raise ProviderError(f"{key}: additional hours without the schedule they belong to")
        merged = {name: list(spans) for name, spans in base[key].items()}
        for groups in extra.get(key, []):
            for name, spans in groups.items():
                merged.setdefault(name, []).extend(spans)
        try:
            built = {name: build_intervals(spans) for name, spans in merged.items()}
        except ValueError as error:
            raise ProviderError(f"{key}: {error}") from error
        days.append(DaySchedule(date=key, groups={g: built[g] for g in sorted(built, key=group_sort_key)}))
    return RegionSchedule(region_id="khmelnytskyi", days=days)
