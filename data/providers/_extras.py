"""What DTEK publishes next to the schedule in force: its notice and the weekly "possible" schedule.

Both come from the DTEK pages, read through the open mirror outage-data-ua (the same data as
`DisconSchedule.attention` and `DisconSchedule.preset` on the page):

- `attention.text`: the operator's notice, e.g. that emergency outages replace the schedule in
  part of the region. Published as `notice`, word for word.
- `preset`: the weekly schedule of possible outages per group and weekday. Published as
  `possible` for today and tomorrow, so people see what may happen when the schedule in force is
  empty or does not apply.
"""

from __future__ import annotations

import re
from datetime import date, datetime, time, timedelta
from typing import Callable

from common.http import get
from common.models import DaySchedule
from common.timeutil import KYIV, today_kyiv

from . import _dtek_site, _mirror

_TAGS = re.compile(r"<[^>]+>")
_GPV = re.compile(r"^GPV(\d+)\.(\d+)$")


def _notice(payload: dict) -> str | None:
    attention = payload.get("attention")
    text = attention.get("text") if isinstance(attention, dict) else attention if isinstance(attention, str) else None
    if not isinstance(text, str):
        return None
    text = _TAGS.sub(" ", text).replace("\r", "")
    lines = [" ".join(line.split()) for line in text.split("\n")]
    text = "\n".join(line for line in lines if line).strip()
    return text[:1000] or None


def _possible(payload: dict, groups: Callable[[str], str | None], today: date) -> list[DaySchedule]:
    preset = payload.get("preset")
    data = preset.get("data") if isinstance(preset, dict) else None
    if not isinstance(data, dict):
        return []
    days: list[DaySchedule] = []
    for day in (today, today + timedelta(days=1)):
        weekday = str(day.isoweekday())
        renamed = {}
        for raw, week in data.items():
            name = groups(raw)
            if name and isinstance(week, dict) and isinstance(week.get(weekday), dict):
                renamed[f"GPV{name}"] = week[weekday]
        if not renamed:
            continue
        stamp = int(datetime.combine(day, time(), KYIV).timestamp())
        fact = {"data": {str(stamp): renamed}, "update": day.strftime("%d.%m.%Y 00:00")}
        days += _dtek_site.parse_fact(fact, "possible").days
    return days


def dtek_groups(raw: str) -> str | None:
    """DTEK's own groups: "GPV2.2" -> "2.2"; CEK's (GPV1001.1 …) are left out."""
    m = _GPV.match(raw)
    return f"{int(m[1])}.{int(m[2])}" if m and int(m[1]) < 1000 else None


def cek_groups(raw: str) -> str | None:
    """CEK groups on DnEM pages: "GPV1003.2" -> "3.2"."""
    m = _GPV.match(raw)
    return f"{int(m[1]) - 1000}.{int(m[2])}" if m and 1001 <= int(m[1]) <= 1099 else None


class DtekExtras:
    """Notice and weekly possible schedule of one DTEK region, from the mirror."""

    def __init__(self, mirror_name: str, groups: Callable[[str], str | None] = dtek_groups) -> None:
        self.url = _mirror.OUTAGE_DATA_UA.format(name=mirror_name)
        self.groups = groups

    def __call__(self) -> tuple[str | None, list[DaySchedule]]:
        payload = get(self.url, accept="application/json").json()
        if not isinstance(payload, dict):
            return None, []
        return _notice(payload), _possible(payload, self.groups, today_kyiv())
