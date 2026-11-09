import json
from pathlib import Path

import pytest

from common.http import ProviderError
from common.models import DaySchedule, Interval, RegionFile, RegionSchedule
from providers import PROVIDERS
from providers import _dtek_site, _yasno
from providers.dtek_dnem import DtekDnem
from providers.dtek_kem import DtekKem
from providers.dtek_oem import DtekOem

FIXTURES = Path(__file__).parent / "fixtures"


def load_json(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def intervals(schedule, date, group):
    day = next(d for d in schedule.days if d.date == date)
    return [(i.from_, i.to, i.type) for i in day.groups[group]]


def as_region_file(schedule, provider) -> RegionFile:
    return RegionFile(
        id=schedule.region_id,
        provider=provider.id,
        sourceUrl=provider.source_url,
        fetchedAt="2026-11-07T16:00:00+02:00",
        updatedAt=schedule.updated_at,
        status="ok",
        emergency=schedule.emergency,
        days=schedule.days,
    )


def test_registry_ids_are_unique():
    ids = [p.id for p in PROVIDERS]
    regions = [r for p in PROVIDERS for r in p.region_ids]
    assert len(ids) == len(set(ids))
    assert len(regions) == len(set(regions))


# --- YASNO API (Kyiv city, Dnipro) ---------------------------------------


def test_kem_fixture():
    provider = DtekKem()
    schedule = provider.parse(load_json("dtek_kem.json"))
    assert schedule.region_id == "kyiv-city"
    assert schedule.updated_at == "2026-11-07T13:45:01+02:00"
    # Tomorrow is "WaitingForSchedule" and must not be published.
    assert [d.date for d in schedule.days] == ["2026-11-07"]
    assert len(schedule.days[0].groups) == 60
    assert intervals(schedule, "2026-11-07", "1.1") == [
        ("00:00", "07:00", "off"),
        ("09:30", "18:30", "off"),
        ("20:00", "24:00", "off"),
    ]
    assert intervals(schedule, "2026-11-07", "3.1") == [
        ("01:00", "08:30", "off"),
        ("11:30", "20:00", "off"),
        ("21:30", "24:00", "off"),
    ]
    as_region_file(schedule, provider)


def test_dnem_fixture():
    provider = DtekDnem()
    schedule = provider.parse(load_json("dtek_dnem.json"))
    assert schedule.region_id == "dnipropetrovsk"
    assert [d.date for d in schedule.days] == ["2026-11-07"]
    assert list(schedule.days[0].groups) == [
        "1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2",
    ]
    as_region_file(schedule, provider)


def _yasno_payload(today_status="ScheduleApplies", tomorrow_status="ScheduleApplies", slots=None):
    slots = slots or [
        {"start": 0, "end": 480, "type": "NotPlanned"},
        {"start": 480, "end": 690, "type": "Definite"},
        {"start": 690, "end": 750, "type": "PossibleOutage"},
        {"start": 750, "end": 1440, "type": "NotPlanned"},
    ]
    return {
        "2.1": {
            "today": {"slots": slots, "date": "2026-03-29T00:00:00+02:00", "status": today_status},
            "tomorrow": {
                "slots": [{"start": 1320, "end": 1440, "type": "Definite"}],
                "date": "2026-03-30T00:00:00+03:00",
                "status": tomorrow_status,
            },
            "updatedOn": "2026-03-29T09:00:00+00:00",
        }
    }


def test_yasno_tomorrow_and_maybe_and_dst_offset():
    schedule = _yasno.parse(_yasno_payload(), "kyiv-city")
    assert [d.date for d in schedule.days] == ["2026-03-29", "2026-03-30"]
    assert intervals(schedule, "2026-03-29", "2.1") == [
        ("08:00", "11:30", "off"),
        ("11:30", "12:30", "maybe"),
    ]
    assert intervals(schedule, "2026-03-30", "2.1") == [("22:00", "24:00", "off")]
    assert schedule.updated_at == "2026-03-29T12:00:00+03:00"


def test_yasno_emergency_flag():
    payload = _yasno_payload(today_status="EmergencyShutdowns", tomorrow_status="WaitingForSchedule")
    schedule = _yasno.parse(payload, "kyiv-city")
    assert schedule.emergency.active is True
    assert [d.date for d in schedule.days] == ["2026-03-29"]


def test_yasno_emergency_without_slots_is_not_power_on():
    # 2026-11-08 Dnipro: EmergencyShutdowns with empty slots used to publish "power all day".
    payload = _yasno_payload(today_status="EmergencyShutdowns", tomorrow_status="EmergencyShutdowns")
    for day in ("today", "tomorrow"):
        payload["2.1"][day]["slots"] = []
    schedule, gaps = _yasno.parse_with_gaps(payload, "dnipropetrovsk")
    assert gaps == ["2026-03-29", "2026-03-30"]
    assert schedule.no_schedules is True
    assert schedule.emergency.active is True


def test_yasno_emergency_gap_dropped_next_to_real_day():
    payload = _yasno_payload(today_status="EmergencyShutdowns")
    payload["2.1"]["today"]["slots"] = [{"start": 0, "end": 1440, "type": "NotPlanned"}]
    schedule, gaps = _yasno.parse_with_gaps(payload, "dnipropetrovsk")
    assert gaps == ["2026-03-29"]
    assert [d.date for d in schedule.days] == ["2026-03-30"]
    assert schedule.no_schedules is False


def test_yasno_fill_from_site():
    payload = _yasno_payload(today_status="EmergencyShutdowns", tomorrow_status="EmergencyShutdowns")
    for day in ("today", "tomorrow"):
        payload["2.1"][day]["slots"] = []
    schedule, gaps = _yasno.parse_with_gaps(payload, "dnipropetrovsk")
    site = RegionSchedule(
        region_id="dnipropetrovsk",
        updated_at="2026-03-29T13:00:00+03:00",
        days=[DaySchedule(date="2026-03-29", groups={"2.1": [Interval(from_="21:00", to="24:00", type="off")]})],
    )
    filled = _yasno.fill_from_site(schedule, gaps, lambda: site)
    assert filled.no_schedules is False
    assert filled.emergency.active is True
    assert [d.date for d in filled.days] == ["2026-03-29"]
    assert filled.updated_at == "2026-03-29T13:00:00+03:00"

    def blocked():
        raise ProviderError("behind a bot challenge")

    assert _yasno.fill_from_site(schedule, gaps, blocked) is schedule


def test_yasno_nothing_published_is_an_error():
    payload = _yasno_payload(today_status="WaitingForSchedule", tomorrow_status="WaitingForSchedule")
    with pytest.raises(ProviderError):
        _yasno.parse(payload, "kyiv-city")


@pytest.mark.parametrize("payload", [{}, [], {"1.1": "oops"}])
def test_yasno_broken_payloads(payload):
    with pytest.raises(ProviderError):
        _yasno.parse(payload, "kyiv-city")


def test_yasno_unknown_slot_type():
    payload = _yasno_payload(slots=[{"start": 0, "end": 60, "type": "Mystery"}])
    with pytest.raises(ProviderError):
        _yasno.parse(payload, "kyiv-city")


# --- DTEK site page (Odesa) ------------------------------------------------


def test_oem_fixture():
    provider = DtekOem()
    schedule = provider.parse((FIXTURES / "dtek_oem.html").read_text(encoding="utf-8"))
    assert schedule.region_id == "odesa"
    assert schedule.updated_at == "2026-11-07T15:39:00+02:00"
    assert [d.date for d in schedule.days] == ["2026-11-07"]
    assert len(schedule.days[0].groups) == 12
    # "first" at hour 23 means no power 22:00-22:30.
    assert intervals(schedule, "2026-11-07", "1.1") == [
        ("05:00", "09:00", "off"),
        ("17:00", "22:30", "off"),
    ]
    # "second" at hour 9 means no power 08:30-09:00, merged with hour 10.
    assert ("08:30", "10:00", "off") in intervals(schedule, "2026-11-07", "3.1")
    as_region_file(schedule, provider)


def _dtek_page(hours: dict[str, str], update="29.03.2026 10:15") -> str:
    full = {str(h): "yes" for h in range(1, 25)} | hours
    fact = {"data": {"1774735200": {"GPV4": full}}, "update": update, "today": 1774735200}
    return f"<script>DisconSchedule.fact = {json.dumps(fact)}\nDisconSchedule.x = 1</script>"


def test_dtek_site_halves_and_maybe():
    html = _dtek_page({"3": "mfirst", "4": "msecond", "5": "maybe", "10": "first", "24": "no"})
    schedule = _dtek_site.parse(html, "odesa")
    assert schedule.days[0].date == "2026-03-29"
    assert intervals(schedule, "2026-03-29", "4") == [
        ("02:00", "02:30", "maybe"),
        ("03:30", "05:00", "maybe"),
        ("09:00", "09:30", "off"),
        ("23:00", "24:00", "off"),
    ]
    assert schedule.updated_at == "2026-03-29T10:15:00+03:00"


def test_dtek_site_unknown_value():
    with pytest.raises(ProviderError):
        _dtek_site.parse(_dtek_page({"5": "sometimes"}), "odesa")


@pytest.mark.parametrize(
    "html",
    [
        "<html>no schedule here</html>",
        "<script>DisconSchedule.fact = {broken</script>",
        "<script>DisconSchedule.fact = {\"data\": {}, \"update\": \"07.11.2026 10:00\"}</script>",
    ],
)
def test_dtek_site_broken_pages(html):
    with pytest.raises(ProviderError):
        _dtek_site.parse(html, "odesa")
