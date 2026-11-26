import asyncio
import gzip
import json
from pathlib import Path

import pytest
from ua_outages.providers.base import Choice, Resolution, Step

from houses.crawl import Address, Miss, Walker, best
from houses.tiles import build, project


def c(i, label):
    return Choice(str(i), label)


def test_best_needs_one_exact_match():
    assert best([c(1, "вул. Наукова"), c(2, "вул. Наукова (Брюховичі)")], "вулиця Наукова").id == "1"
    assert best([c(1, "Шевченка Тараса")], "вулиця Тараса Шевченка").id == "1"
    assert best([c(1, "Петрівка"), c(2, "с. Петрівка")], "Петрівка") is None  # ambiguous
    assert best([c(1, "12-А"), c(2, "12")], "12а", house=True).id == "1"
    assert best([c(1, "12/1")], "12", house=True) is None


class FakeLookup:
    """Area → city → street → house, like Zhytomyr's directory."""

    steps = (
        Step("Оберіть РЕМ (район)", browse=True, in_label=False),
        Step("Напишіть назву населеного пункту", browse=True),
        Step("Напишіть назву вулиці", browse=True),
        Step("Напишіть номер будинку", browse=True, house=True),
    )
    tree = {
        "a1": {"Озерне": {"Лісова": {"5": "1.1"}}},
        "a2": {"Озерне": {"Садова": {"7": "2.2"}}, "Житомир": {"Київська": {"49": "3.1", "49А": "3.2"}}},
    }

    async def options(self, http, path, query):
        node = self.tree
        for p in path:
            node = node[p]
        return [c(k, k) for k in node]

    async def resolve(self, http, path):
        node = self.tree
        for p in path:
            node = node[p]
        return Resolution((node,))


def find(addr):
    w = Walker(FakeLookup(), None)
    return asyncio.run(w.find(addr))


def test_walker_finds_the_area_by_city_and_street():
    assert find(Address("zhytomyr", "Житомир", "вулиця Київська", "", "49А")) == ("a2", "Житомир", "Київська", "49А")
    assert find(Address("zhytomyr", "Озерне", "Садова", "", "7")) == ("a2", "Озерне", "Садова", "7")


def test_walker_misses_are_specific():
    with pytest.raises(Miss, match="street"):
        find(Address("zhytomyr", "Озерне", "Польова", "", "1"))
    with pytest.raises(Miss, match="house"):
        find(Address("zhytomyr", "Житомир", "Київська", "", "50"))
    with pytest.raises(Miss, match="city"):
        find(Address("zhytomyr", "Бердичів", "Київська", "", "1"))


def test_old_street_name_is_tried():
    assert find(Address("zhytomyr", "Озерне", "Нова", "Лісова", "5"))[2] == "Лісова"


def test_tiles_put_buildings_where_the_map_draws_them(tmp_path: Path):
    osm = tmp_path / "osm"
    osm.mkdir()
    with gzip.open(osm / "buildings.tsv.gz", "wt", encoding="utf-8") as f:
        f.write("region\tlon\tlat\tcity\tcity_from\tstreet\told_street\tnumber\n")
        f.write("lviv\t24.03\t49.84\tЛьвів\ttag\tНаукова\t\t1\n")
    with gzip.open(osm / "roads.tsv.gz", "wt", encoding="utf-8") as f:
        f.write("")
    cache = tmp_path / "cache"
    cache.mkdir()
    key = Address("lviv", "Львів", "Наукова", "", "1").key
    with gzip.open(cache / "loe.json.gz", "wt", encoding="utf-8") as f:
        json.dump({key: {"g": ["1.2"], "t": "2026-11-26"}}, f)
    index = build(osm, cache, tmp_path / "site", "2026-11-26T12:00:00+02:00")
    [(name, count)] = index["tiles"].items()
    tile = json.loads((tmp_path / "site/v1/houses" / f"{name}.json").read_text())
    x, y = project(24.03, 49.84)
    tx, ty = tile["tile"]
    assert abs(tx * 8 + tile["x"][0] / 1024 - x) < 0.002 and abs(ty * 8 + tile["y"][0] / 1024 - y) < 0.002
    assert tile["regions"] == ["lviv"] and tile["groups"] == [[0, "1.2"]] and tile["g"] == [0]
    assert index["coverage"]["lviv"] == {"houses": 1, "withGroup": 1}
    streets = json.loads((tmp_path / "site/v1/houses/streets.json").read_text())
    assert streets["items"] == [[0, "Львів", "Наукова", [name]]]


def test_same_name_handles_kinds_initials_and_order():
    from houses.crawl import same_name

    assert same_name("вулиця Тараса Шевченка", "Шевченка Т. вул.")
    assert same_name("вул. Героїв Майдану", "Героїв Майдану")
    assert not same_name("вулиця Шевченка", "пров. Шевченка")
    assert same_name("проспект Миру", "пр-т Миру")
    assert same_name("вулиця Кам’янецька", "вул. Кам'янецька")
    assert not same_name("вулиця Садова", "вулиця Садова Нова")


def test_notes_in_parentheses_are_not_part_of_the_name():
    assert best([c(1, "Хмельницький (Хмельницький РЕМ)")], "Хмельницький").id == "1"
    assert best([c(1, "Петрівка (Петриківський р-н)"), c(2, "Петрівка (Царичанський р-н)")], "Петрівка") is None
    assert best([c(1, "12 (гуртожиток)")], "12", house=True).id == "1"


def test_ordinals_match_whatever_the_ending():
    from houses.crawl import _bare, same_name

    assert same_name("1-ша Нова вулиця", "вул. Нова 1-а")
    assert not same_name("1-ша Нова вулиця", "вул. Нова 2-а")
    assert _bare("1-ша Нова вулиця") == "Нова"
    assert _bare("вулиця Кам’янецька") == "Кам’янецька"
