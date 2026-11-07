import json
from datetime import date, datetime, timedelta
from pathlib import Path

import pytest

from common.http import NotPublished, ProviderError
from common.models import RegionFile
from common.publish import ProviderResult, publish
from common.text import normalize, parse_ranges, resolve_day_month
from common.timeutil import KYIV
from providers import AUTOMATIC, all_providers
from providers import (
    cherkasyoblenergo,
    khmelnytskoblenergo,
    lvivoblenergo,
    mykolaivoblenergo,
    rivneoblenergo,
    zaporizhzhiaoblenergo,
    zhytomyroblenergo,
)
from providers.manual import ManualProvider, manual_providers

FIXTURES = Path(__file__).parent / "fixtures"
TODAY = date(2026, 11, 7)
GROUPS_12 = ["1.1", "1.2", "2.1", "2.2", "3.1", "3.2", "4.1", "4.2", "5.1", "5.2", "6.1", "6.2"]


def text(name: str) -> str:
    return (FIXTURES / name).read_text(encoding="utf-8")


def load(name: str):
    return json.loads(text(name))


def iv(schedule, group, day="2026-11-07"):
    found = next(d for d in schedule.days if d.date == day)
    return [(i.from_, i.to, i.type) for i in found.groups[group]]


def valid(schedule, provider_id="test"):
    RegionFile(
        id=schedule.region_id,
        provider=provider_id,
        sourceUrl="https://example.org",
        fetchedAt="2026-11-07T19:00:00+02:00",
        updatedAt=schedule.updated_at or "2026-11-07T19:00:00+02:00",
        status="ok",
        days=schedule.days,
    )


def test_registry_has_unique_regions():
    regions = [r for p in AUTOMATIC for r in p.region_ids]
    assert len(regions) == len(set(regions))


# --- text helpers ------------------------------------------------------------


def test_normalize_repairs_split_times():
    assert normalize("17 :00 - 21:00 , 20:3 0") == "17:00 - 21:00 , 20:30"
    assert parse_ranges("16 :00 - 20:3 0 , 22 :00 - 24:00") == [(960, 1230, "off"), (1320, 1440, "off")]
    assert parse_ranges("02:30 – 06:00; 23:30 – 23:59") == [(150, 360, "off"), (1410, 1440, "off")]


def test_year_is_resolved_across_new_year():
    assert resolve_day_month(1, 1, date(2026, 12, 31)) == date(2027, 1, 1)
    assert resolve_day_month(31, 12, date(2027, 1, 1)) == date(2026, 12, 31)


# --- providers on real fixtures ------------------------------------------------


def test_khmelnytskyi():
    s = khmelnytskoblenergo.parse(text("khmelnytskoblenergo.html"), TODAY)
    assert [d.date for d in s.days] == ["2026-11-07"]
    assert list(s.days[0].groups) == GROUPS_12
    assert iv(s, "1.1") == [("00:00", "04:00", "off"), ("09:00", "13:00", "off"), ("18:00", "21:00", "off")]
    assert iv(s, "2.1")[-1] == ("22:00", "24:00", "off")
    valid(s)


def test_khmelnytskyi_old_schedule_is_not_published():
    with pytest.raises(NotPublished):
        khmelnytskoblenergo.parse(text("khmelnytskoblenergo.html"), TODAY + timedelta(days=1))


def test_zaporizhzhia_takes_newest_post_for_today():
    s = zaporizhzhiaoblenergo.parse(text("zaporizhzhiaoblenergo.html"), TODAY)
    assert [d.date for d in s.days] == ["2026-11-07"]
    assert list(s.days[0].groups) == GROUPS_12
    assert iv(s, "1.1") == [("00:00", "02:00", "off"), ("06:00", "11:00", "off"), ("15:00", "20:00", "off")]
    assert iv(s, "2.2") == [("13:00", "18:00", "off"), ("22:30", "24:00", "off")]
    valid(s)


def test_zaporizhzhia_uses_latest_update_of_a_day():
    s = zaporizhzhiaoblenergo.parse(text("zaporizhzhiaoblenergo.html"), date(2026, 11, 5))
    # Newest update of 5 November (21:25) comes first on the page.
    assert s.days[0].date == "2026-11-05"
    assert [d.date for d in s.days] == ["2026-11-05", "2026-11-06", "2026-11-07"]


def test_zhytomyr_half_hour_cells():
    s = zhytomyroblenergo.parse(
        (FIXTURES / "zhytomyroblenergo.html").read_bytes().decode("cp1251"), TODAY
    )
    assert s.updated_at == "2026-11-07T19:00:00+02:00"
    assert list(s.days[0].groups) == GROUPS_12
    assert iv(s, "1.1")[0] == ("00:00", "02:00", "off")
    valid(s)


def test_rivne_waiting_is_not_published():
    with pytest.raises(NotPublished):
        rivneoblenergo.parse(text("rivneoblenergo.html"), TODAY)


def test_rivne_published_hours():
    page = text("rivneoblenergo.html").replace(
        "<p style=\"white-space:nowrap; margin:0;\">Очікується</p>",
        "<p style=\"white-space:nowrap; margin:0;\">08:00 - 12:00<br>18:00 - 20:00</p>",
    )
    s = rivneoblenergo.parse(page, TODAY)
    assert s.updated_at == "2026-11-07T19:00:00+02:00"
    assert list(s.days[0].groups) == GROUPS_12
    assert iv(s, "6.2") == [("08:00", "12:00", "off"), ("18:00", "20:00", "off")]


def test_rivne_unknown_cell_text_is_an_error():
    page = text("rivneoblenergo.html").replace("Очікується", "щось нове")
    with pytest.raises(ProviderError):
        rivneoblenergo.parse(page, TODAY)


def test_cherkasy_listing_and_post():
    links = cherkasyoblenergo.latest_posts(text("cherkasyoblenergo_list.html"), TODAY)
    assert links == [(TODAY, "/media/onovleno-hrafik-pohodynnykh-vidklyuchen-hpv-na-7-lystopada-2?lang=uk")]
    s = cherkasyoblenergo.parse_posts([(TODAY, text("cherkasyoblenergo_post.html"))], TODAY)
    assert s.updated_at == "2026-11-07T16:28:00+02:00"
    assert list(s.days[0].groups) == GROUPS_12
    assert iv(s, "1.1") == [("17:00", "21:00", "off"), ("23:00", "24:00", "off")]
    assert iv(s, "6.2") == [("16:00", "20:30", "off"), ("22:00", "24:00", "off")]
    valid(s)


def test_cherkasy_nothing_for_tomorrow():
    with pytest.raises(NotPublished):
        cherkasyoblenergo.latest_posts(text("cherkasyoblenergo_list.html"), TODAY + timedelta(days=1))


def test_mykolaiv_api():
    s = mykolaivoblenergo.parse(
        load("mykolaivoblenergo_schedule.json"),
        load("mykolaivoblenergo_queues.json"),
        load("mykolaivoblenergo_series.json"),
    )
    assert [d.date for d in s.days] == ["2026-11-07"]
    assert s.updated_at == "2026-11-07T16:10:16+02:00"
    assert list(s.days[0].groups) == GROUPS_12
    # Matches the operator's news text "1.1 – 02:30 – 06:00; 09:30 – 16:30 ..." (off + possible).
    assert iv(s, "1.1")[:2] == [("02:30", "03:00", "maybe"), ("03:00", "05:30", "off")]
    valid(s)


def test_mykolaiv_unknown_type():
    schedule = load("mykolaivoblenergo_schedule.json")
    schedule[0]["series"][0]["type"] = "SOMETHING"
    with pytest.raises(ProviderError):
        mykolaivoblenergo.parse(schedule, load("mykolaivoblenergo_queues.json"), load("mykolaivoblenergo_series.json"))


def test_lviv_latest_item_for_a_date():
    s = lvivoblenergo.parse(load("lvivoblenergo.json"), date(2026, 10, 17))
    assert s.updated_at == "2026-10-17T18:56:00+03:00"
    assert iv(s, "1.1", "2026-10-17") == [("20:00", "22:00", "off")]
    assert iv(s, "3.1", "2026-10-17") == [("16:00", "19:00", "off")]
    assert not s.no_schedules


def test_lviv_without_todays_item_means_no_schedules():
    s = lvivoblenergo.parse(load("lvivoblenergo.json"), TODAY)
    assert s.no_schedules
    assert [d.date for d in s.days] == ["2026-11-07"]
    assert all(v == [] for v in s.days[0].groups.values())
    assert list(s.days[0].groups) == GROUPS_12


# --- manual files --------------------------------------------------------------


def manual_file(tmp_path, region="kyiv", day="2099-01-01", groups=None):
    path = tmp_path / f"{region}.json"
    path.write_text(
        json.dumps(
            {
                "sourceUrl": "https://example.org",
                "updatedAt": "2026-11-07T18:00:00+02:00",
                "days": [{"date": day, "groups": groups or {"1.1": [{"from": "08:00", "to": "12:00", "type": "off"}]}}],
            }
        ),
        encoding="utf-8",
    )
    return path


def test_manual_file(tmp_path):
    provider = ManualProvider(manual_file(tmp_path))
    [s] = provider.fetch()
    assert provider.region_ids == ["kyiv"] and provider.source_url == "https://example.org"
    assert s.days[0].date == "2099-01-01"


def test_manual_file_in_the_past(tmp_path):
    with pytest.raises(NotPublished):
        ManualProvider(manual_file(tmp_path, day="2020-01-01")).fetch()


def test_manual_file_invalid(tmp_path):
    path = manual_file(tmp_path, groups={"1.1": [{"from": "12:00", "to": "08:00", "type": "off"}]})
    with pytest.raises(ProviderError):
        ManualProvider(path).fetch()


def test_manual_ignores_unknown_regions(tmp_path):
    manual_file(tmp_path, region="atlantis")
    manual_file(tmp_path, region="donetsk")
    assert [p.region_ids for p in manual_providers(tmp_path)] == [["donetsk"]]


def test_manual_never_shadows_automatic_provider():
    covered = [r for p in all_providers() for r in p.region_ids]
    assert len(covered) == len(set(covered))


# --- publishing details -------------------------------------------------------


def test_updated_at_is_kept_while_schedule_is_unchanged(tmp_path):
    s = khmelnytskoblenergo.parse(text("khmelnytskoblenergo.html"), TODAY)
    now = datetime(2026, 11, 7, 19, 0, tzinfo=KYIV)
    result = lambda: ProviderResult("khmelnytskoblenergo", "https://example.org", ["khmelnytskyi"], [s])
    publish(tmp_path, [result()], now)
    later = publish(tmp_path, [result()], now + timedelta(minutes=10))
    assert later.written == []
    region = json.loads((tmp_path / "v1/regions/khmelnytskyi.json").read_text(encoding="utf-8"))
    assert region["updatedAt"] == "2026-11-07T19:00:00+02:00"


def test_skipped_provider_keeps_published_data(tmp_path):
    s = khmelnytskoblenergo.parse(text("khmelnytskoblenergo.html"), TODAY)
    now = datetime(2026, 11, 7, 19, 0, tzinfo=KYIV)
    publish(tmp_path, [ProviderResult("x", "https://example.org", ["khmelnytskyi"], [s])], now)
    skipped = ProviderResult("x", "https://example.org", ["khmelnytskyi"], skipped=True)
    report = publish(tmp_path, [skipped], now + timedelta(minutes=10))
    assert report.stale == [] and report.written == []


def test_no_schedules_status(tmp_path):
    s = lvivoblenergo.parse(load("lvivoblenergo.json"), TODAY)
    publish(tmp_path, [ProviderResult("lvivoblenergo", "https://example.org", ["lviv"], [s])], datetime(2026, 11, 7, 19, tzinfo=KYIV))
    index = json.loads((tmp_path / "v1/index.json").read_text(encoding="utf-8"))
    assert index["regions"][0]["status"] == "no_schedules"
    assert index["regions"][0]["groups"] == GROUPS_12
