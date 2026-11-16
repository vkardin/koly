"""Open mirrors of operators' schedules, published on GitHub in the DTEK `fact` format.

Used only where the operator's own source cannot be read by a polite client (bot challenge,
data by address only, hours only on a picture) or does not answer GitHub's servers:

- github.com/Baskerville42/outage-data-ua (MIT): DTEK regions; refreshed every few minutes.
- github.com/yaroslav2901/OE_OUTAGE_DATA: oblenergos; a file changes only when its schedule does.

A mirror is someone else's copy, so it is checked before use: it must have refreshed
recently, and a region without a day for today is "schedules not applied" only while the
mirror as a whole is alive.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, timedelta

from common.http import NotPublished, ProviderError, get
from common.models import DaySchedule, RegionSchedule

from . import _dtek_site

OUTAGE_DATA_UA = "https://raw.githubusercontent.com/Baskerville42/outage-data-ua/main/data/{name}.json"
OE_OUTAGE_DATA = "https://raw.githubusercontent.com/yaroslav2901/OE_OUTAGE_DATA/main/data/{name}.json"
# Files of OE_OUTAGE_DATA that change daily while schedules apply anywhere: show the mirror is alive.
_OE_SENTINELS = ("Khmelnytskoblenerho", "Zaporizhzhiaoblenergo", "Chernihivoblenergo")


@dataclass(frozen=True)
class Mirror:
    url: str
    page: str
    # The file itself must have refreshed within this time (None: it changes only with the schedule).
    max_age: timedelta | None = None
    # Other files of the same mirror; one of them refreshed within `alive_age` shows it works.
    sentinels: tuple[str, ...] = ()
    alive_age: timedelta = timedelta(hours=36)


def outage_data_ua(name: str) -> Mirror:
    return Mirror(
        OUTAGE_DATA_UA.format(name=name),
        "https://github.com/Baskerville42/outage-data-ua",
        max_age=timedelta(hours=3),
    )


def oe_outage_data(name: str) -> Mirror:
    return Mirror(
        OE_OUTAGE_DATA.format(name=name),
        "https://github.com/yaroslav2901/OE_OUTAGE_DATA",
        sentinels=tuple(OE_OUTAGE_DATA.format(name=n) for n in _OE_SENTINELS if n != name),
    )


def refreshed_at(payload: object) -> datetime:
    if not isinstance(payload, dict):
        raise ProviderError("mirror: expected an object")
    try:
        moment = datetime.fromisoformat(str(payload.get("lastUpdated")).replace("Z", "+00:00"))
    except ValueError as error:
        raise ProviderError("mirror: no lastUpdated") from error
    if moment.tzinfo is None:
        raise ProviderError("mirror: lastUpdated without a time zone")
    return moment


def parse(payload: object, region_id: str, today: date, now: datetime, mirror: Mirror, alive: bool) -> RegionSchedule:
    """`alive` tells whether the mirror as a whole refreshed recently (see `Mirror.sentinels`)."""
    refreshed = refreshed_at(payload)
    status = payload.get("lastUpdateStatus")
    if isinstance(status, dict) and status.get("ok") is False:
        raise ProviderError(f"mirror: its last update failed (code {status.get('code')!r})")
    if mirror.max_age is not None and now - refreshed > mirror.max_age:
        raise ProviderError(f"mirror: last refreshed at {refreshed.isoformat()}")
    schedule = _dtek_site.parse_fact(payload.get("fact"), region_id)
    days = [d for d in schedule.days if date.fromisoformat(d.date) >= today]
    if days:
        return schedule.model_copy(update={"days": days})
    if not (alive or now - refreshed <= mirror.alive_age):
        raise NotPublished("mirror: nothing for today and the mirror has not refreshed lately")
    # The mirror works and has no day for today: schedules are not applied in the region.
    groups = list(schedule.days[-1].groups)
    return RegionSchedule(
        region_id=region_id,
        days=[DaySchedule(date=today.isoformat(), groups={g: [] for g in groups})],
        no_schedules=True,
    )


def fetch(mirror: Mirror, region_id: str, today: date, now: datetime) -> RegionSchedule:
    payload = get(mirror.url, accept="application/json").json()
    alive = False
    if mirror.sentinels and now - refreshed_at(payload) > mirror.alive_age:
        for url in mirror.sentinels:
            try:
                if now - refreshed_at(get(url, accept="application/json").json()) <= mirror.alive_age:
                    alive = True
                    break
            except (ProviderError, ValueError):
                continue
    return parse(payload, region_id, today, now, mirror, alive)
