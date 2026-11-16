import json
from datetime import date, datetime, timedelta
from pathlib import Path

import pytest
from ua_outages import EMERGENCY, KYIV
from ua_outages import DaySchedule as LibraryDay
from ua_outages import Outage

from common.http import NotPublished, ProviderError
from providers import AUTOMATIC, _mirror
from providers.uamonitor import REGIONS, convert

FIXTURES = Path(__file__).parent / "fixtures"
TODAY = date(2026, 11, 16)
NOW = datetime(2026, 11, 16, 16, 30, tzinfo=KYIV)
GROUPS = tuple(f"{q}.{s}" for q in range(1, 7) for s in (1, 2))


def load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def at(day: date, hour: int, minute: int = 0) -> datetime:
    return datetime(day.year, day.month, day.day, tzinfo=KYIV) + timedelta(hours=hour, minutes=minute)


def iv(schedule, group, index=0):
    return [(i.from_, i.to, i.type) for i in schedule.days[index].groups[group]]


def test_regions_are_registered_once():
    ids = [r for p in AUTOMATIC for r in p.region_ids]
    assert len(ids) == len(set(ids))
    assert {p.region_ids[0] for p in REGIONS} == {
        "kyiv", "volyn", "zakarpattia", "ivano-frankivsk", "kirovohrad",
        "poltava", "sumy", "ternopil", "kharkiv", "chernihiv",
    }  # fmt: skip


def test_mirror_kyiv_region():
    s = _mirror.parse(load("mirror_kyiv_region.json"), "kyiv", TODAY, NOW, _mirror.outage_data_ua("kyiv-region"), False)
    assert s.updated_at == "2026-11-15T20:28:00+02:00"
    assert list(s.days[0].groups) == list(GROUPS)
    assert iv(s, "1.1") == [("08:30", "12:30", "off"), ("19:00", "24:00", "off")]


def test_mirror_that_stopped_refreshing_is_an_error():
    with pytest.raises(ProviderError):
        _mirror.parse(
            load("mirror_kyiv_region.json"), "kyiv", TODAY, NOW + timedelta(hours=4),
            _mirror.outage_data_ua("kyiv-region"), False,
        )


def test_mirror_failed_update_is_an_error():
    payload = load("mirror_kyiv_region.json")
    payload["lastUpdateStatus"] = {"ok": False, "code": 422}
    with pytest.raises(ProviderError):
        _mirror.parse(payload, "kyiv", TODAY, NOW, _mirror.outage_data_ua("kyiv-region"), False)


def test_mirror_old_day_means_no_schedules_only_while_alive():
    mirror = _mirror.oe_outage_data("Ternopiloblenerho")
    payload = load("mirror_ternopil.json")  # last schedule is from October
    s = _mirror.parse(payload, "ternopil", TODAY, NOW, mirror, True)
    assert s.no_schedules and s.days[0].date == "2026-11-16"
    assert list(s.days[0].groups) == list(GROUPS) and not any(s.days[0].groups.values())
    with pytest.raises(NotPublished):
        _mirror.parse(payload, "ternopil", TODAY, NOW, mirror, False)


def test_convert_outages_possible_and_midnight():
    tomorrow = TODAY + timedelta(days=1)
    schedules = {
        TODAY: LibraryDay(
            TODAY,
            updated_at=at(TODAY, 9, 15),
            groups={
                "1.1": (Outage(at(TODAY, 8), at(TODAY, 12)), Outage(at(TODAY, 12), at(TODAY, 12, 30), possible=True)),
                "6.2": (Outage(at(TODAY, 22), at(tomorrow, 0)),),
            },
        ),
        TODAY - timedelta(days=1): LibraryDay(TODAY - timedelta(days=1), groups={"1.1": ()}),
    }
    s = convert(schedules, "volyn", GROUPS, TODAY)
    assert [d.date for d in s.days] == ["2026-11-16"]
    assert s.updated_at == "2026-11-16T09:15:00+02:00"
    assert iv(s, "1.1") == [("08:00", "12:00", "off"), ("12:00", "12:30", "maybe")]
    assert iv(s, "6.2") == [("22:00", "24:00", "off")]
    assert iv(s, "3.1") == []  # a group the schedule does not list has power


def test_convert_nothing_published_means_no_schedules():
    s = convert({}, "kirovohrad", GROUPS, TODAY)
    assert s.no_schedules and list(s.days[0].groups) == list(GROUPS)


def test_convert_unread_hours_are_an_error():
    schedules = {TODAY: LibraryDay(TODAY, groups={"1.1": ()}, unknown={"2.1": "08:00 - ??"})}
    with pytest.raises(ProviderError):
        convert(schedules, "sumy", GROUPS, TODAY)


def test_convert_unknown_group_is_an_error():
    with pytest.raises(ProviderError):
        convert({TODAY: LibraryDay(TODAY, groups={"7.1": ()})}, "sumy", GROUPS, TODAY)


def test_convert_emergency_day_has_no_hours():
    schedules = {TODAY: LibraryDay(TODAY, unknown={g: EMERGENCY for g in GROUPS})}
    s = convert(schedules, "kharkiv", GROUPS, TODAY)
    assert s.emergency.active and s.no_schedules


def test_convert_only_past_days_is_not_published():
    yesterday = TODAY - timedelta(days=1)
    with pytest.raises(NotPublished):
        convert({yesterday: LibraryDay(yesterday, groups={"1.1": ()})}, "volyn", GROUPS, TODAY)
