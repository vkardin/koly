"""Regions read through the open library `ukraine-energy-monitor` (MIT), with open mirrors as a fallback.

The library (github.com/fastiuk/ukraine-energy-monitor, pinned in requirements.txt) reads the
operators' own sites, APIs and official Telegram channels, and does not work around bot
protection. Each region here tries the library's sources in order, then a mirror
(see `_mirror`): for sites that do not answer GitHub's servers, give data only by address,
or publish the hours only as a picture.
"""

from __future__ import annotations

import asyncio
from datetime import date, datetime

from common.http import USER_AGENT, NotPublished, ProviderError
from common.intervals import Span, build_intervals
from common.models import DaySchedule, Emergency, RegionSchedule
from common.timeutil import iso_kyiv, now_kyiv, today_kyiv

from . import _mirror


def convert(schedules: dict, region_id: str, groups: tuple[str, ...], today: date) -> RegionSchedule:
    """Library result (`dict[date, ua_outages.DaySchedule]`) to the published model."""
    from ua_outages import EMERGENCY

    if not schedules:
        # The source answered and lists nothing: schedules are not applied.
        return RegionSchedule(
            region_id=region_id,
            days=[DaySchedule(date=today.isoformat(), groups={g: [] for g in groups})],
            no_schedules=True,
        )
    days: list[DaySchedule] = []
    stamps: list[datetime] = []
    emergency = False
    for day in sorted(schedules):
        schedule = schedules[day]
        if day < today:
            continue
        if any(text == EMERGENCY for text in schedule.unknown.values()):
            emergency = True  # emergency outages replace the schedule: the day has no hours
            continue
        if unparsed := schedule.unparsed_groups():
            raise ProviderError(f"{day}: hours not read for groups {unparsed}")
        if extra := set(schedule.groups) - set(groups):
            raise ProviderError(f"{day}: unknown groups {sorted(extra)}")
        parsed = {}
        for group in groups:
            spans: list[Span] = []
            for outage in schedule.groups.get(group, ()):
                start = outage.start.hour * 60 + outage.start.minute if outage.start.date() == day else 0
                end = outage.end.hour * 60 + outage.end.minute if outage.end.date() == day else 1440
                if end == 1439:
                    end = 1440  # "till 23:59" is the end of the day
                if outage.end.date() < day or outage.start.date() > day:
                    continue
                spans.append((start, end, "maybe" if outage.possible else "off"))
            try:
                parsed[group] = build_intervals(spans)
            except ValueError as error:
                raise ProviderError(f"group {group} {day}: {error}") from error
        days.append(DaySchedule(date=day.isoformat(), groups=parsed))
        if schedule.updated_at:
            stamps.append(schedule.updated_at)
    if not days:
        if emergency:
            return RegionSchedule(
                region_id=region_id,
                days=[DaySchedule(date=today.isoformat(), groups={g: [] for g in groups})],
                emergency=Emergency(active=True),
                no_schedules=True,
            )
        raise NotPublished("no schedule for today or later")
    return RegionSchedule(
        region_id=region_id,
        updated_at=iso_kyiv(max(stamps)) if stamps else None,
        days=days,
        emergency=Emergency(active=emergency),
    )


async def _library(operator_id: str, region_id: str, today: date) -> RegionSchedule:
    import aiohttp
    from ua_outages import AddressSource, Fetcher, NotApplied, now_kyiv as library_now, operator

    op = operator(operator_id)
    if op is None:
        raise ProviderError(f"unknown operator {operator_id}")
    sources = [s for s in op.sources if not isinstance(s, AddressSource) and not s.hours_on_picture]
    if not sources:
        raise ProviderError("no source that lists hours for every group")
    errors: list[str] = []
    async with aiohttp.ClientSession() as session:
        for source in sources:
            try:
                http = Fetcher(session, source.hosts, timeout=25, user_agent=USER_AGENT)
                result = await source.fetch(http, library_now())
            except NotApplied:
                result = {}
            except Exception as error:  # every failure moves on to the next source
                errors.append(f"{source.name}: {type(error).__name__}: {error}"[:200])
                continue
            try:
                return convert(result, region_id, tuple(op.groups), today)
            except NotPublished:
                raise
            except ProviderError as error:
                errors.append(f"{source.name}: {error}"[:200])
    raise ProviderError("; ".join(errors))


class UaMonitor:
    """One region: the library's sources for `operator_id` (if any), then `mirror` (if any)."""

    def __init__(self, provider_id: str, region_id: str, source_url: str, operator_id: str | None = None,
                 mirror: _mirror.Mirror | None = None) -> None:
        self.id = provider_id
        self.region_ids = [region_id]
        self.source_url = source_url
        self.operator_id = operator_id
        self.mirror = mirror

    def fetch(self) -> list[RegionSchedule]:
        region_id, today = self.region_ids[0], today_kyiv()
        reasons: list[str] = []
        not_published = False
        if self.operator_id:
            try:
                return [asyncio.run(_library(self.operator_id, region_id, today))]
            except NotPublished as error:
                not_published = True
                reasons.append(str(error))
            except ProviderError as error:
                reasons.append(str(error))
        if self.mirror:
            try:
                return [_mirror.fetch(self.mirror, region_id, today, now_kyiv())]
            except NotPublished as error:
                not_published = True
                reasons.append(str(error))
            except Exception as error:  # a mirror is someone else's file: anything can be in it
                reasons.append(f"mirror: {type(error).__name__}: {error}"[:200])
        if not_published:
            raise NotPublished("; ".join(reasons))
        raise ProviderError("; ".join(reasons))


def _checked(provider_id: str, region_id: str, url: str, operator_id: str, oe_name: str | None = None):
    """A library region; its open mirror (if any) is both the check and the fallback."""
    from ._checks import Checked, mirror

    checks = [mirror(_mirror.oe_outage_data(oe_name), "oe-outage-data")] if oe_name else []
    return Checked(UaMonitor(provider_id, region_id, url, operator_id), checks)


REGIONS: list = [
    UaMonitor("dtek-krem", "kyiv", "https://www.dtek-krem.com.ua/ua/shutdowns",
              mirror=_mirror.outage_data_ua("kyiv-region")),
    _checked("cek", "dnipropetrovsk-cek", "https://cek.dp.ua", "cek"),
    _checked("volynoblenergo", "volyn",
             "https://energy.volyn.ua/spozhyvacham/perervy-u-elektropostachanni/hrafik-vidkliuchen/", "volyn-oe"),
    _checked("zakarpattiaoblenergo", "zakarpattia",
             "https://zakarpat.energy/customers/break-in-electricity-supply/schedule/", "zakarpattia-oe",
             "Zakarpattiaoblenerho"),
    _checked("prykarpattiaoblenergo", "ivano-frankivsk", "https://svitlo.oe.if.ua", "prykarpattia-oe",
             "Prykarpattiaoblenerho"),
    _checked("kirovohradoblenergo", "kirovohrad", "https://kiroe.com.ua/electricity-blackout", "kirovohrad-oe"),
    _checked("poltavaoblenergo", "poltava", "https://www.poe.pl.ua/disconnection/power-outages/", "poltava-oe",
             "Poltavaoblenergo"),
    _checked("sumyoblenergo", "sumy", "https://www.soe.com.ua/consumers/shutdowns", "sumy-oe"),
    UaMonitor("ternopiloblenergo", "ternopil", "https://poweron.toe.com.ua",
              mirror=_mirror.oe_outage_data("Ternopiloblenerho")),
    _checked("kharkivoblenergo", "kharkiv", "https://oblenergo.kharkiv.ua/uk/media_centre/news", "kharkiv-oe",
             "Kharkivoblenerho"),
    UaMonitor("chernihivoblenergo", "chernihiv", "https://chernihivoblenergo.com.ua/blackouts",
              mirror=_mirror.oe_outage_data("Chernihivoblenergo")),
]


class WithMirror:
    """A provider of this project with a mirror behind it, for sources that often fail from GitHub's servers."""

    def __init__(self, provider, mirror: _mirror.Mirror) -> None:
        self.provider = provider
        self.mirror = mirror
        self.id = provider.id
        self.region_ids = list(provider.region_ids)
        self.source_url = provider.source_url
        if hasattr(provider, "min_interval"):
            self.min_interval = provider.min_interval

    def fetch(self) -> list[RegionSchedule]:
        try:
            return self.provider.fetch()
        except NotPublished:
            raise
        except Exception as error:
            try:
                return [_mirror.fetch(self.mirror, self.region_ids[0], today_kyiv(), now_kyiv())]
            except Exception as mirror_error:
                raise ProviderError(f"{error}; mirror: {mirror_error}"[:400]) from error
