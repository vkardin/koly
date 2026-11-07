"""Cherkasyoblenergo (Cherkasy region): schedules published as news posts.

The home page lists posts newest first ("Оновлено графік погодинних відключень
(ГПВ) на 7 листопада"). A post body reads:
    "Години відсутності електропостачання: 1.1 17 :00 - 21:00 , 23 :00 - 24:00 1.2 ..."
"""

from __future__ import annotations

import re
from datetime import date, datetime

from common.http import NotPublished, ProviderError, get
from common.intervals import build_intervals
from common.models import DaySchedule, RegionSchedule, group_sort_key
from common.text import MONTH_PATTERN, MONTHS_GENITIVE, html_to_text, parse_ranges, resolve_day_month
from common.timeutil import KYIV, iso_kyiv, today_kyiv

BASE = "https://www.cherkasyoblenergo.com"
_LINK = re.compile(r'href="(/media/[^"]*hrafik-pohodynnykh-vidklyuchen[^"]*)"[^>]*>(.*?)</a>', re.S)
_TITLE_DATE = re.compile(rf"\bна\s+(\d{{1,2}})\s+({MONTH_PATTERN})", re.I)
_GROUP = re.compile(r"(?<![\d.:])([1-6]\.[12])\s+((?:\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}[\s,;]*)+)")
_POSTED = re.compile(r"(\d{2}\.\d{2}\.\d{4})\s+(\d{1,2}:\d{2})")
MAX_POSTS = 3


class Cherkasyoblenergo:
    id = "cherkasyoblenergo"
    region_ids = ["cherkasy"]
    source_url = f"{BASE}/?lang=uk"

    def fetch(self) -> list[RegionSchedule]:
        today = today_kyiv()
        links = latest_posts(get(self.source_url, accept="text/html").text, today)
        posts = [(day, get(BASE + path, accept="text/html").text) for day, path in links]
        return [parse_posts(posts, today)]


def latest_posts(listing: str, today: date) -> list[tuple[date, str]]:
    """Newest post per date, today or later."""
    found: dict[date, str] = {}
    for path, title in _LINK.findall(listing):
        match = _TITLE_DATE.search(html_to_text(title))
        if not match:
            continue
        day = resolve_day_month(int(match.group(1)), MONTHS_GENITIVE[match.group(2).lower()], today)
        if day >= today and day not in found:
            found[day] = path
    if not found:
        raise NotPublished("no schedule post for today or later")
    return sorted(found.items())[:MAX_POSTS]


def parse_post(raw: str) -> tuple[dict, str | None]:
    text = html_to_text(raw)
    start = text.find("Години відсутності електропостачання")
    if start < 0:
        raise ProviderError("post has no outage hours")
    body = text[start:]
    end = body.find("Свою чергу")
    body = body[: end if end > 0 else 2000]
    groups = {}
    for name, ranges in _GROUP.findall(body):
        try:
            groups[name] = build_intervals(parse_ranges(ranges))
        except ValueError as error:
            raise ProviderError(f"group {name}: {error}") from error
    if len(groups) < 6:
        raise ProviderError(f"only {len(groups)} groups parsed")
    posted = _POSTED.search(text[: start])
    stamp = None
    if posted:
        moment = datetime.strptime(f"{posted.group(1)} {posted.group(2)}", "%d.%m.%Y %H:%M")
        stamp = iso_kyiv(moment.replace(tzinfo=KYIV))
    return groups, stamp


def parse_posts(posts: list[tuple[date, str]], today: date) -> RegionSchedule:
    days, stamps = [], []
    for day, raw in posts:
        groups, stamp = parse_post(raw)
        if stamp:
            stamps.append(stamp)
        days.append(
            DaySchedule(date=day.isoformat(), groups={g: groups[g] for g in sorted(groups, key=group_sort_key)})
        )
    if not days:
        raise NotPublished("no schedule for today or later")
    return RegionSchedule(region_id="cherkasy", updated_at=max(stamps) if stamps else None, days=days)
