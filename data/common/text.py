"""Helpers for sources that publish schedules as free text or HTML tables."""

from __future__ import annotations

import html as html_lib
import re
from datetime import date

from .intervals import Span

MONTHS_GENITIVE = {
    "січня": 1, "лютого": 2, "березня": 3, "квітня": 4, "травня": 5, "червня": 6,
    "липня": 7, "серпня": 8, "вересня": 9, "жовтня": 10, "листопада": 11, "грудня": 12,
}
MONTH_PATTERN = "|".join(MONTHS_GENITIVE)

_TAGS = re.compile(r"<[^>]+>")
_SKIP = re.compile(r"<(script|style)\b.*?</\1>", re.S | re.I)
_RANGE = re.compile(r"(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})")


def html_to_text(raw: str) -> str:
    text = _SKIP.sub(" ", raw)
    text = re.sub(r"<br\s*/?>|</p>|</div>|</tr>|</li>", "\n", text, flags=re.I)
    text = html_lib.unescape(_TAGS.sub(" ", text))
    return normalize(text)


def normalize(text: str) -> str:
    """Unifies dashes and spaces and repairs split times like "17 :00" or "20:3 0"."""
    text = text.replace("\xa0", " ")
    text = re.sub(r"[‐-―−]", "-", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\b(\d{1,2}) :(\d)", r"\1:\2", text)
    text = re.sub(r"(\d:\d) (\d)\b", r"\1\2", text)
    return text


def minutes(hours: str, mins: str, *, end: bool = False) -> int:
    value = int(hours) * 60 + int(mins)
    if end and value in (0, 1439):  # "до 00:00" / "до 23:59" mean the end of the day
        return 1440
    if not 0 <= value <= 1440 or int(mins) > 59:
        raise ValueError(f"invalid time {hours}:{mins}")
    return value


def parse_ranges(text: str, kind: str = "off") -> list[Span]:
    """Finds every "HH:MM - HH:MM" in text."""
    spans: list[Span] = []
    for h1, m1, h2, m2 in _RANGE.findall(normalize(text)):
        start, end = minutes(h1, m1), minutes(h2, m2, end=True)
        if end <= start:
            raise ValueError(f"reversed range {h1}:{m1}-{h2}:{m2}")
        spans.append((start, end, kind))
    return spans


def resolve_day_month(day: int, month: int, today: date) -> date:
    """Picks the year so that the date is closest to today (handles New Year)."""
    candidates = [date(today.year + delta, month, day) for delta in (-1, 0, 1)]
    return min(candidates, key=lambda d: abs((d - today).days))


def find_day_month(text: str, today: date) -> date | None:
    match = re.search(rf"(\d{{1,2}})\s+({MONTH_PATTERN})", text, re.I)
    if not match:
        return None
    return resolve_day_month(int(match.group(1)), MONTHS_GENITIVE[match.group(2).lower()], today)
