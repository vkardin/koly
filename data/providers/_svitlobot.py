"""Open timetables of Svitlobot (github.com/Svitlobot-official/timetables), licence CC BY 4.0.

One more independent copy of the schedules, used like the other mirrors: every region is
checked against it, and where the operator's own source and the other mirrors all fail it
is published instead. Svitlobot itself collects the schedules from open repositories that
parse the operators' sites, so it is the least trusted source of a region (it comes last).

Attribution (the licence asks for it): data © Svitlobot, https://svitlobot.in.ua, CC BY 4.0.

Layout: `index.json` lists every group file with its update time, `<region>/<group>.json`
holds the week of one group:

    {"group": "1.1", "updated_at": "...", "source_updated_at": "...",
     "days": [{"date": "2026-11-28", "slots": [...24...], "outages": [["08:00","10:00"], ...]}]}

`outages` is `null` for a day not published yet and `[]` for a day without outages.
Hours are Europe/Kyiv.
"""

from __future__ import annotations

import re
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime
from threading import Lock

from common.http import NotPublished, ProviderError, get
from common.intervals import build_intervals
from common.models import DaySchedule, RegionSchedule, group_sort_key, hm_to_minutes
from common.timeutil import iso_kyiv, now_kyiv, today_kyiv

BASE = "https://raw.githubusercontent.com/Svitlobot-official/timetables/main/"
PAGE = "https://github.com/Svitlobot-official/timetables"
SITE = "https://svitlobot.in.ua"

# Region id of this project -> folder of Svitlobot.
FOLDERS: dict[str, str] = {
    "kyiv-city": "kyiv",
    "kyiv": "kyiv_oblast",
    "dnipropetrovsk": "dnipro_dtek",
    "dnipropetrovsk-cek": "dnipro_cek",
    "odesa": "odesa",
    "lviv": "lviv",
    "khmelnytskyi": "khmelnytskyi",
    "zaporizhzhia": "zaporizhzhia",
    "zhytomyr": "zhytomyr",
    "rivne": "rivne",
    "cherkasy": "cherkasy",
    "chernihiv": "chernihiv",
    "kharkiv": "kharkiv",
    "poltava": "poltava",
    "ternopil": "ternopil",
    "ivano-frankivsk": "ivano_frankivsk",
    "zakarpattia": "zakarpattia",
    "vinnytsia": "vinnytsia",
}

_GROUP = re.compile(r"^\d{1,2}\.\d$")
_INDEX: dict = {}
_LOCK = Lock()


def wanted_group(region_id: str, group: str) -> bool:
    """Svitlobot keeps files of groups an operator no longer has; only today's numbering is read."""
    if not _GROUP.match(group):
        return False
    queue, sub = group.split(".")
    if region_id == "dnipropetrovsk":  # since 26.11.2026: 18 queues 1.1 … 18.1
        return sub == "1" and 1 <= int(queue) <= 18
    if region_id == "dnipropetrovsk-cek":
        return 1 <= int(queue) <= 6 and sub in "12"
    if region_id == "kyiv-city":
        return sub == "1" and 1 <= int(queue) <= 60
    return 1 <= int(queue) <= 6 and sub in "12"


def _json(path: str) -> dict:
    return get(BASE + path, accept="application/json").json()


def index() -> dict:
    """index.json, read once per run."""
    with _LOCK:
        if "data" not in _INDEX:
            _INDEX["data"] = _json("index.json")
        return _INDEX["data"]


def parse(files: list[dict], region_id: str, today: date) -> RegionSchedule:
    """Group files of one region to the published model (days from today on)."""
    if not files:
        raise ProviderError("svitlobot: no group files")
    by_date: dict[str, dict[str, list]] = {}
    stamps: list[datetime] = []
    for data in files:
        group = str(data.get("group"))
        stamp = data.get("source_updated_at") or data.get("updated_at")
        if stamp:
            stamps.append(datetime.fromisoformat(stamp))
        for day in data.get("days") or []:
            if date.fromisoformat(day["date"]) < today or day.get("outages") is None:
                continue
            try:
                spans = [(hm_to_minutes(a), hm_to_minutes(b), "off") for a, b in day["outages"]]
                if any(end <= start for start, end, _ in spans):
                    raise ValueError("empty or reversed hours")
                by_date.setdefault(day["date"], {})[group] = build_intervals(spans)
            except (ValueError, TypeError, KeyError) as error:
                raise ProviderError(f"svitlobot: group {group} {day.get('date')}: {error}") from error
    if today.isoformat() not in by_date:
        # The week of the files does not reach today: the mirror has stopped or has not caught up.
        raise NotPublished("svitlobot: nothing for today")
    names = {g for groups in by_date.values() for g in groups}
    days = []
    for d in sorted(by_date):
        groups = by_date[d]
        if set(groups) != names:
            continue  # a day only some groups have yet is not a schedule
        days.append(DaySchedule(date=d, groups={g: groups[g] for g in sorted(groups, key=group_sort_key)}))
    if not days or days[0].date != today.isoformat():
        raise NotPublished("svitlobot: today is not complete")
    updated = iso_kyiv(max(stamps)) if stamps else None
    if all(not iv for day in days for iv in day.groups.values()):
        # Nothing but empty days: the operator applies no schedules.
        return RegionSchedule(region_id=region_id, updated_at=updated, days=days[:1], no_schedules=True)
    return RegionSchedule(region_id=region_id, updated_at=updated, days=days)


def fetch(region_id: str, today: date | None = None) -> RegionSchedule:
    folder = FOLDERS[region_id]
    today = today or today_kyiv()
    entries = [e for e in index()["groups"] if e["region"] == folder and wanted_group(region_id, e["group"])]
    if not entries:
        raise ProviderError(f"svitlobot: no files for {folder}")
    with ThreadPoolExecutor(max_workers=6) as pool:
        files = list(pool.map(lambda e: _json(e["file"]), entries))
    return parse(files, region_id, today)


class SvitlobotRegion:
    """A region whose only source is Svitlobot (no usable source of the operator itself)."""

    def __init__(self, provider_id: str, region_id: str) -> None:
        self.id = provider_id
        self.region_ids = [region_id]
        self.source_url = SITE

    def fetch(self) -> list[RegionSchedule]:
        return [fetch(self.region_ids[0], today_kyiv(now_kyiv()))]
