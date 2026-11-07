import json
from datetime import datetime, timedelta

import pytest
from pydantic import ValidationError

from common.intervals import build_intervals
from common.models import DaySchedule, Interval, RegionSchedule
from common.publish import ProviderResult, publish
from common.timeutil import KYIV

NOW = datetime(2026, 11, 7, 16, 0, tzinfo=KYIV)


def schedule(off_until="08:00", region="odesa"):
    day = DaySchedule(
        date="2026-11-07",
        groups={"1.1": [Interval(**{"from": "00:00", "to": off_until, "type": "off"})], "1.2": []},
    )
    return RegionSchedule(region_id=region, updated_at="2026-11-07T15:39:00+02:00", days=[day])


def ok(*schedules):
    return ProviderResult("dtek-oem", "https://example.org", ["odesa"], list(schedules))


def failed():
    return ProviderResult("dtek-oem", "https://example.org", ["odesa"], error="boom")


def read(site, path):
    return json.loads((site / path).read_text(encoding="utf-8"))


def test_first_publish_writes_region_and_index(tmp_path):
    report = publish(tmp_path, [ok(schedule())], NOW)
    assert report.written == ["v1/regions/odesa.json", "v1/index.json"]
    region = read(tmp_path, "v1/regions/odesa.json")
    assert region["status"] == "ok"
    assert region["fetchedAt"] == "2026-11-07T16:00:00+02:00"
    assert region["days"][0]["groups"]["1.1"] == [{"from": "00:00", "to": "08:00", "type": "off"}]
    index = read(tmp_path, "v1/index.json")
    assert index["schemaVersion"] == 1
    assert index["regions"][0]["id"] == "odesa"
    assert index["regions"][0]["groups"] == ["1.1", "1.2"]
    assert index["regions"][0]["name"]["uk"] == "Одеська"
    assert (tmp_path / ".nojekyll").exists()


def test_unchanged_data_is_not_rewritten(tmp_path):
    publish(tmp_path, [ok(schedule())], NOW)
    report = publish(tmp_path, [ok(schedule())], NOW + timedelta(minutes=10))
    assert report.written == []


def test_heartbeat_rewrites_after_an_hour(tmp_path):
    publish(tmp_path, [ok(schedule())], NOW)
    report = publish(tmp_path, [ok(schedule())], NOW + timedelta(minutes=61))
    assert "v1/regions/odesa.json" in report.written


def test_changed_schedule_is_written(tmp_path):
    publish(tmp_path, [ok(schedule())], NOW)
    report = publish(tmp_path, [ok(schedule("09:00"))], NOW + timedelta(minutes=10))
    assert report.written == ["v1/regions/odesa.json"]


def test_failure_keeps_data_and_marks_stale(tmp_path):
    publish(tmp_path, [ok(schedule())], NOW)
    before = read(tmp_path, "v1/regions/odesa.json")
    report = publish(tmp_path, [failed()], NOW + timedelta(minutes=10))
    after = read(tmp_path, "v1/regions/odesa.json")
    assert report.stale == ["odesa"]
    assert after["status"] == "stale"
    assert after["days"] == before["days"]
    assert after["fetchedAt"] == before["fetchedAt"]
    assert read(tmp_path, "v1/index.json")["regions"][0]["status"] == "stale"
    # A second failure changes nothing.
    assert publish(tmp_path, [failed()], NOW + timedelta(minutes=20)).written == []
    # Recovery publishes ok again.
    publish(tmp_path, [ok(schedule())], NOW + timedelta(minutes=30))
    assert read(tmp_path, "v1/regions/odesa.json")["status"] == "ok"


def test_failure_without_previous_data_writes_nothing(tmp_path):
    report = publish(tmp_path, [failed()], NOW)
    assert report.failed == ["odesa"]
    assert not (tmp_path / "v1").exists()


def test_missing_region_in_result_is_treated_as_failure(tmp_path):
    publish(tmp_path, [ok(schedule())], NOW)
    report = publish(tmp_path, [ok()], NOW + timedelta(minutes=10))
    assert report.stale == ["odesa"]


def test_broken_published_file_is_replaced(tmp_path):
    path = tmp_path / "v1" / "regions" / "odesa.json"
    path.parent.mkdir(parents=True)
    path.write_text("{not json", encoding="utf-8")
    publish(tmp_path, [ok(schedule())], NOW)
    assert read(tmp_path, "v1/regions/odesa.json")["status"] == "ok"


def test_build_intervals_merges_and_rejects_conflicts():
    merged = build_intervals([(60, 120, "off"), (0, 60, "off"), (120, 150, "maybe"), (200, 200, "off")])
    assert [(i.from_, i.to, i.type) for i in merged] == [
        ("00:00", "02:00", "off"),
        ("02:00", "02:30", "maybe"),
    ]
    with pytest.raises(ValueError):
        build_intervals([(0, 60, "off"), (30, 90, "maybe")])
    with pytest.raises(ValueError):
        build_intervals([(0, 1500, "off")])


@pytest.mark.parametrize(
    "bad",
    [
        {"from": "10:00", "to": "09:00", "type": "off"},
        {"from": "24:00", "to": "24:00", "type": "off"},
        {"from": "9:00", "to": "10:00", "type": "off"},
        {"from": "09:00", "to": "10:00", "type": "on"},
    ],
)
def test_invalid_intervals_are_rejected(bad):
    with pytest.raises(ValidationError):
        Interval(**bad)


def test_overlapping_intervals_in_a_day_are_rejected():
    with pytest.raises(ValidationError):
        DaySchedule(
            date="2026-11-07",
            groups={
                "1.1": [
                    Interval(**{"from": "08:00", "to": "10:00", "type": "off"}),
                    Interval(**{"from": "09:00", "to": "11:00", "type": "off"}),
                ]
            },
        )
