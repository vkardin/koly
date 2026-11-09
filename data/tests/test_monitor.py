import json
from datetime import datetime, timedelta

from common.models import DaySchedule, RegionSchedule
from common.publish import ProviderResult, publish
from common.timeutil import KYIV
from monitor import manual_reminders, stale_regions

NOW = datetime(2026, 11, 7, 12, 0, tzinfo=KYIV)


def seed(site):
    day = DaySchedule(date="2026-11-07", groups={"1.1": []})
    schedule = RegionSchedule(region_id="odesa", updated_at="2026-11-07T11:00:00+02:00", days=[day])
    publish(site, [ProviderResult("dtek-oem", "https://example.org", ["odesa"], [schedule])], NOW)


def fail(site, at):
    publish(site, [ProviderResult("dtek-oem", "https://example.org", ["odesa"], error="boom")], at)


def test_fresh_data_has_no_alert(tmp_path):
    seed(tmp_path)
    assert stale_regions(tmp_path, NOW + timedelta(hours=10)) == []


def test_short_outage_has_no_alert(tmp_path):
    seed(tmp_path)
    fail(tmp_path, NOW + timedelta(minutes=10))
    assert stale_regions(tmp_path, NOW + timedelta(hours=2)) == []


def test_long_outage_alerts_with_a_stable_line(tmp_path):
    seed(tmp_path)
    fail(tmp_path, NOW + timedelta(minutes=10))
    first = stale_regions(tmp_path, NOW + timedelta(hours=7))
    later = stale_regions(tmp_path, NOW + timedelta(hours=9))
    assert len(first) == 1 and "odesa" in first[0]
    assert first == later


def test_broken_file_alerts(tmp_path):
    seed(tmp_path)
    (tmp_path / "v1/regions/odesa.json").write_text(json.dumps({"id": "odesa"}), encoding="utf-8")
    assert "not valid" in stale_regions(tmp_path, NOW)[0]


def seed_emergency(site, groups, no_schedules=False):
    from common.models import Emergency, Interval

    groups = {g: [Interval(**i) for i in v] for g, v in groups.items()}
    day = DaySchedule(date="2026-11-07", groups=groups)
    schedule = RegionSchedule(
        region_id="dnipropetrovsk",
        updated_at="2026-11-07T11:00:00+02:00",
        days=[day],
        emergency=Emergency(active=True),
        no_schedules=no_schedules,
    )
    publish(site, [ProviderResult("dtek-dnem", "https://example.org", ["dnipropetrovsk"], [schedule])], NOW)


HOURS = {"1.1": [{"from": "08:00", "to": "12:00", "type": "off"}]}


def test_emergency_without_hours_asks_for_a_manual_schedule(tmp_path):
    seed_emergency(tmp_path / "site", {"1.1": []})
    [line] = manual_reminders(tmp_path / "site", NOW, tmp_path)
    assert "dnipropetrovsk" in line and "no hours for today" in line
    seed_emergency(tmp_path / "site2", {"1.1": []}, no_schedules=True)
    assert len(manual_reminders(tmp_path / "site2", NOW, tmp_path)) == 1


def test_emergency_with_hours_reminds_about_tomorrow_in_the_evening(tmp_path):
    seed_emergency(tmp_path / "site", HOURS)
    manual = tmp_path / "dnipropetrovsk.json"
    manual.write_text('{"days": [{"date": "2026-11-07"}]}', encoding="utf-8")
    assert manual_reminders(tmp_path / "site", NOW, tmp_path) == []
    evening = NOW.replace(hour=20, minute=30)
    [line] = manual_reminders(tmp_path / "site", evening, tmp_path)
    assert "2026-11-08" in line
    manual.write_text('{"days": [{"date": "2026-11-07"}, {"date": "2026-11-08"}]}', encoding="utf-8")
    assert manual_reminders(tmp_path / "site", evening, tmp_path) == []


def test_no_reminder_without_emergency(tmp_path):
    seed(tmp_path)
    assert manual_reminders(tmp_path, NOW, tmp_path) == []
