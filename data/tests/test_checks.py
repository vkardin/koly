from datetime import timedelta

from common.http import NotPublished, ProviderError
from common.models import DaySchedule, Interval, RegionSchedule
from common.timeutil import today_kyiv
from providers._checks import Checked, Source, compare

TODAY = today_kyiv()
T = TODAY.isoformat()
TOMORROW = (TODAY + timedelta(days=1)).isoformat()


def day(date, **groups):
    return DaySchedule(date=date, groups={
        g.replace("_", "."): [Interval(**{"from": a, "to": b, "type": "off"}) for a, b in spans]
        for g, spans in groups.items()
    })


def sched(*days, no=False):
    return RegionSchedule(region_id="lviv", days=list(days), no_schedules=no)


def test_same_when_shared_groups_agree():
    a = sched(day(T, **{"1_1": [("08:00", "12:00")], "1_2": []}))
    b = sched(day(T, **{"1_1": [("08:00", "12:00")]}))
    assert compare(a, b, T) == ("same", [])


def test_different_hours_are_reported_with_the_other_version():
    a = sched(day(T, **{"1_1": [("08:00", "12:00")]}))
    b = sched(day(T, **{"1_1": [("08:00", "12:30")]}))
    result, days = compare(a, b, T)
    assert result == "different"
    assert days[0].groups["1.1"][0].to == "12:30"


def test_day_only_the_other_source_has_is_reported():
    a = sched(day(T, **{"1_1": []}))
    b = sched(day(T, **{"1_1": []}), day(TOMORROW, **{"1_1": [("00:00", "04:00")]}))
    result, days = compare(a, b, T)
    assert result == "different" and [d.date for d in days] == [TOMORROW]


def test_nothing_in_common_gives_no_verdict():
    a = sched(day(T, **{"1_1": []}))
    b = sched(day(T, **{"9_9": []}))
    assert compare(a, b, T)[0] == "none"


class Fake:
    id = "fake"
    region_ids = ["lviv"]
    source_url = "https://example.org"

    def __init__(self, result):
        self.result = result

    def fetch(self):
        if isinstance(self.result, Exception):
            raise self.result
        return [self.result]


def test_checked_records_agreement_and_disagreement():
    main = sched(day(T, **{"1_1": [("08:00", "12:00")]}))
    other = sched(day(T, **{"1_1": [("09:00", "12:00")]}))
    p = Checked(Fake(main), [
        Source("a", "mirror", "A", "https://a", lambda r: main),
        Source("b", "mirror", "B", "https://b", lambda r: other),
        Source("c", "mirror", "C", "https://c", lambda r: (_ for _ in ()).throw(ProviderError("down"))),
        Source("d", "mirror", "D", "https://d", lambda r: (_ for _ in ()).throw(NotPublished("not yet"))),
    ])
    [out] = p.fetch()
    assert [(c.source, c.result) for c in out.checks] == [("a", "same"), ("b", "different"), ("c", "failed")]
    assert out.checks[1].days[0].groups["1.1"][0].from_ == "09:00"


def test_checked_falls_back_to_the_first_working_source():
    other = sched(day(T, **{"1_1": [("09:00", "12:00")]}))
    p = Checked(Fake(ProviderError("parse")), [
        Source("x", "mirror", "X", "https://x", lambda r: (_ for _ in ()).throw(ProviderError("down"))),
        Source("y", "mirror", "Y", "https://y", lambda r: other),
    ])
    [out] = p.fetch()
    assert out.days == other.days
    assert [(c.source, c.result) for c in out.checks] == [("x", "failed")]


def test_checked_keeps_not_published_when_nothing_has_data():
    p = Checked(Fake(NotPublished("none")), [
        Source("x", "mirror", "X", "https://x", lambda r: (_ for _ in ()).throw(NotPublished("none"))),
    ])
    try:
        p.fetch()
    except NotPublished:
        pass
    else:
        raise AssertionError("expected NotPublished")
