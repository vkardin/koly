import json
from datetime import datetime, timedelta

from common.models import DaySchedule, RegionSchedule
from common.publish import ProviderResult, publish
from common.timeutil import KYIV
from monitor import stale_regions

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
