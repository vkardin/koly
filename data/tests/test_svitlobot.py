import json
from datetime import date
from pathlib import Path

import pytest

from common.http import NotPublished, ProviderError
from providers import _svitlobot as sb
from providers._checks import compare

FIXTURES = Path(__file__).parent / "fixtures" / "svitlobot"
TODAY = date(2026, 11, 28)


def files():
    return [json.loads(p.read_text(encoding="utf-8")) for p in sorted(FIXTURES.glob("*.json"))]


def test_parse_week_files():
    s = sb.parse(files(), "vinnytsia", TODAY)
    assert s.region_id == "vinnytsia"
    # 29 November is published as null: not a day yet.
    assert [d.date for d in s.days] == ["2026-11-28"]
    assert list(s.days[0].groups) == ["1.1", "2.2", "6.1"]
    first = [(i.from_, i.to) for i in s.days[0].groups["1.1"]]
    assert first and all(i < j for i, j in first)
    assert s.updated_at and s.updated_at.startswith("2026-11-28")


def test_today_missing_is_not_published():
    with pytest.raises(NotPublished):
        sb.parse(files(), "vinnytsia", date(2026, 12, 20))


def test_null_day_is_skipped_and_empty_week_means_no_schedules():
    data = files()
    for f in data:
        for d in f["days"]:
            d["outages"] = [] if d["outages"] is not None else None
    s = sb.parse(data, "vinnytsia", TODAY)
    assert s.no_schedules


def test_bad_hours_fail_loudly():
    data = files()
    data[0]["days"][5]["outages"] = [["10:00", "09:00"]]
    with pytest.raises(ProviderError):
        sb.parse(data, "vinnytsia", TODAY)


def test_only_current_numbering_is_read():
    assert sb.wanted_group("dnipropetrovsk", "18.1")
    assert not sb.wanted_group("dnipropetrovsk", "1.2")  # old 6 x 2 numbering
    assert sb.wanted_group("dnipropetrovsk-cek", "6.2")
    assert not sb.wanted_group("dnipropetrovsk-cek", "1")
    assert sb.wanted_group("kyiv-city", "60.1") and not sb.wanted_group("kyiv-city", "1.2")
    assert sb.wanted_group("lviv", "3.2") and not sb.wanted_group("lviv", "7.1")


def test_agrees_with_itself_when_compared():
    s = sb.parse(files(), "vinnytsia", TODAY)
    assert compare(s, s, TODAY.isoformat())[0] == "same"


def test_every_region_folder_is_known_to_the_project():
    from common.regions import REGION_NAMES

    assert set(sb.FOLDERS) <= set(REGION_NAMES)


def test_hours_only_comparison_ignores_possible_outages():
    from common.models import DaySchedule, Interval, RegionSchedule

    def sched(parts):
        iv = [Interval(**{"from": a, "to": b, "type": t}) for a, b, t in parts]
        return RegionSchedule(region_id="x", days=[DaySchedule(date="2026-11-28", groups={"1.1": iv})])

    mirror = sched([("10:00", "13:00", "off"), ("13:00", "13:30", "maybe")])
    flat = sched([("10:00", "13:30", "off")])
    assert compare(mirror, flat, "2026-11-28")[0] == "different"
    assert compare(mirror, flat, "2026-11-28", hours_only=True)[0] == "same"
    other = sched([("10:00", "14:00", "off")])
    assert compare(mirror, other, "2026-11-28", hours_only=True)[0] == "different"
