"""Finds the outage group of every addressed building in the operators' open address directories.

    python -m houses.crawl --buildings out/buildings.tsv.gz --cache cache/ --crawler yasno-kem --budget 300

The directories are the ones the operators offer on their own sites for "find your group by
address" (read through the open library `ukraine-energy-monitor`). They are walked slowly:
one request at a time, at least a second apart (more where the library asks for it), every
answer cached, so a full pass over a region takes days of small runs, not a burst. Endpoints
behind a captcha, personal accounts and anything by name are never used.

Accuracy over coverage: a building gets a group only when its settlement, street and number
each match exactly one directory entry. Anything ambiguous stays without a group.

Results go to `cache/<crawler>.json.gz`: address key → {"g": [groups], "t": date} or
{"m": why it was not found, "t": date}. Entries older than `--max-age` days are looked up again.
"""

from __future__ import annotations

import argparse
import asyncio
import csv
import gzip
import json
import logging
import time
from collections import Counter
from dataclasses import dataclass, field
from datetime import date, timedelta
from pathlib import Path
from typing import Iterable

log = logging.getLogger("koly.houses.crawl")


@dataclass(frozen=True)
class Crawler:
    id: str
    operator: str  # operator id in ukraine-energy-monitor
    region: str  # where the buildings are (map region)
    publishes_as: str  # region id whose schedule the groups belong to
    # Only addresses another crawler could not place (e.g. CEK after DTEK in Dnipropetrovsk).
    after: str | None = None


CRAWLERS: dict[str, Crawler] = {c.id: c for c in [
    Crawler("yasno-kem", "dtek-kem", "kyiv-city", "kyiv-city"),
    Crawler("yasno-dnem", "dtek-dnem", "dnipropetrovsk", "dnipropetrovsk"),
    Crawler("yasno-cek", "cek", "dnipropetrovsk", "dnipropetrovsk-cek", after="yasno-dnem"),
    Crawler("loe", "lviv-oe", "lviv", "lviv"),
    Crawler("ternopil", "ternopil-oe", "ternopil", "ternopil"),
    Crawler("khmelnytskyi", "khmelnytskyi-oe", "khmelnytskyi", "khmelnytskyi"),
    Crawler("mykolaiv", "mykolaiv-oe", "mykolaiv", "mykolaiv"),
    Crawler("zhytomyr", "zhytomyr-oe", "zhytomyr", "zhytomyr"),
]}

MIN_GAP = 1.0  # seconds between requests to one directory, whatever the library allows
# Raised whenever matching improves: addresses missed by an older matcher are looked up again.
MATCHER = 5


@dataclass
class Address:
    region: str
    city: str
    street: str
    old_street: str
    number: str

    @property
    def key(self) -> str:
        return "|".join(x.casefold() for x in (self.region, self.city, self.street, self.number))


def read_buildings(path: Path, region: str | None = None) -> Iterable[tuple[Address, float, float]]:
    with gzip.open(path, "rt", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            if region and row["region"] != region:
                continue
            yield (Address(row["region"], row["city"], row["street"], row["old_street"], row["number"]),
                   float(row["lon"]), float(row["lat"]))


def load_cache(path: Path) -> dict[str, dict]:
    if not path.exists():
        return {}
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return json.load(f)


def save_cache(path: Path, cache: dict[str, dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    with gzip.open(tmp, "wt", encoding="utf-8", compresslevel=9) as f:
        json.dump(cache, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    tmp.replace(path)


# ---- matching directory entries ----

# Kinds of street and settlement, in their usual spellings. Two names match when their words match
# once these are set aside, and their kinds agree whenever both names give one.
_KINDS = {
    "вулиця": "вул", "вул": "вул", "ул": "вул",
    "провулок": "пров", "пров": "пров", "пер": "пров",
    "проспект": "просп", "просп": "просп", "пр-т": "просп", "пр": "просп",
    "бульвар": "бул", "бульв": "бул", "бул": "бул",
    "площа": "пл", "пл": "пл", "майдан": "майдан",
    "тупик": "туп", "туп": "туп", "узвіз": "узвіз", "шосе": "шосе", "набережна": "наб", "наб": "наб",
    "проїзд": "проїзд", "алея": "алея", "в'їзд": "вїзд", "вїзд": "вїзд", "лінія": "лінія", "квартал": "кв", "кв": "кв",
    "мікрорайон": "мкр", "мкр": "мкр", "масив": "масив", "урочище": "ур",
    "місто": "", "м": "", "село": "", "с": "", "селище": "", "смт": "", "с-ще": "", "сщ": "", "хутір": "", "х": "",
}
_TOKEN = __import__("re").compile(r"[^\W_]+(?:['’ʼ-][^\W_]+)*")
# "1-ша", "1-й", "2-га", "3-я": an ordinal is its number, whatever the ending.
_ORDINAL = __import__("re").compile(r"^([0-9]{1,3})(?:-?(?:й|ий|ій|а|ша|га|тя|я|е|ге|ше|те))?$")


_PARENS = __import__("re").compile(r"\([^()]*\)")


def _parts(text: str) -> tuple[str | None, list[str]]:
    from ua_outages.providers._address import fold

    text = _PARENS.sub(" ", text)  # "Хмельницький (Хмельницький РЕМ)": the note is not the name
    kind = None
    words: list[str] = []
    for tok in _TOKEN.findall(fold(text).replace("’", "'").replace("ʼ", "'")):
        if tok in _KINDS:
            kind = kind or (_KINDS[tok] or None)
        elif m := _ORDINAL.match(tok):
            words.append(m[1])
        else:
            words.append(tok.replace("'", ""))
    return kind, words


def _bare(text: str) -> str:
    """The name to search a directory by: no kind of street, no note, apostrophes kept."""
    from ua_outages.providers._address import fold

    words = [t for t in _TOKEN.findall(_PARENS.sub(" ", text)) if fold(t).replace("’", "'").replace("ʼ", "'") not in _KINDS]
    # Directories spell ordinals their own way ("1-а Нова", "Нова 1-ша"): search by the other words.
    named = [w for w in words if not _ORDINAL.match(fold(w))]
    return " ".join(named or words) or text


def same_name(a: str, b: str) -> bool:
    """ "вулиця Тараса Шевченка" = "Шевченка Т. вул." ≠ "пров. Шевченка"."""
    ka, wa = _parts(a)
    kb, wb = _parts(b)
    if ka and kb and ka != kb:
        return False
    if not wa or not wb:
        return False
    if sorted(wa) == sorted(wb):
        return True
    if len(wa) != len(wb):
        return False
    # Initials: "Т Шевченка" for "Тараса Шевченка".
    full_a = sorted(w for w in wa if len(w) > 1)
    full_b = sorted(w for w in wb if len(w) > 1)
    ini_a = sorted(w[0] for w in wa if len(w) == 1)
    ini_b = sorted(w[0] for w in wb if len(w) == 1)
    if ini_a and not ini_b:
        rest = list(full_b)
        for w in full_a:
            if w not in rest:
                return False
            rest.remove(w)
        return sorted(w[0] for w in rest) == ini_a
    if ini_b and not ini_a:
        return same_name(b, a)
    return False


def _house(text: str) -> str:
    from ua_outages.providers._address import compact, strip_kind

    return compact(strip_kind(_PARENS.sub(" ", text))).replace("корпус", "к").replace("корп", "к")


def best(choices, query: str, house: bool = False):
    """The one choice that names `query`, or None when there is none or more than one."""
    if house:
        want = _house(query)
        exact = [c for c in choices if _house(c.label) == want]
        return exact[0] if len(exact) == 1 else None
    found = [c for c in choices if same_name(c.label, query)]
    if len(found) > 1:
        # "Наукова" and "Наукова (Брюховичі)": the one without a note is the place itself.
        found = [c for c in found if not _PARENS.search(c.label)]
    return found[0] if len(found) == 1 else None


class Miss(Exception):
    """The address is not (unambiguously) in the directory; the reason is the message."""


@dataclass
class Walker:
    lookup: object
    http: object
    options_cache: dict = field(default_factory=dict)
    requests: int = 0
    samples: dict = field(default_factory=dict)

    async def options(self, path: tuple[str, ...], query: str) -> list:
        key = (path, query)
        if key not in self.options_cache:
            self.requests += 1
            self.options_cache[key] = await self.lookup.options(self.http, list(path), query)
            if len(self.options_cache) > 50_000:
                self.options_cache.clear()
        return self.options_cache[key]

    def _role(self, step) -> str:
        if step.house:
            return "house"
        prompt = step.prompt.casefold()
        if "вулиц" in prompt:
            return "street"
        if "населен" in prompt:
            return "city"
        return "area"  # a district or branch of the operator, which OSM does not have

    async def _pick(self, step, path: tuple[str, ...], query: str, role: str):
        if not query:
            raise Miss(role)
        # Even for a step that can list everything, search by the name: lists are cut at 40 entries,
        # a search puts the exact match first (and the library caches the whole list it filters).
        # Directories search by the name alone: "Інженерна вулиця" finds nothing, "Інженерна" does.
        search = query if step.house else _bare(query)
        choices = await self.options(path, search)
        found = best(choices, query, house=step.house)
        if found is None:
            if self.samples.get(role, 0) < 12:
                self.samples[role] = self.samples.get(role, 0) + 1
                log.info("no %s match for %r among %s", role, query, [c.label for c in choices[:6]])
            raise Miss(role)
        return found

    async def find(self, addr: Address, path: tuple[str, ...] = (), start: int = 0) -> tuple[str, ...]:
        steps = self.lookup.steps
        for i in range(start, len(steps)):
            step = steps[i]
            role = self._role(step)
            if role == "area":
                if not step.browse:
                    raise Miss("area")
                # Try every area: the same village name can be in two of them.
                found: list[tuple[str, ...]] = []
                misses: list[str] = []
                for area in await self.options(path, ""):
                    try:
                        found.append(await self.find(addr, path + (area.id,), i + 1))
                    except Miss as miss:
                        misses.append(str(miss))
                if len(found) == 1:
                    return found[0]
                if found:
                    raise Miss("ambiguous")
                for reason in ("ambiguous", "house", "street"):
                    if reason in misses:
                        raise Miss(reason)
                raise Miss("city")
            if role == "street":
                try:
                    choice = await self._pick(step, path, addr.street, "street")
                except Miss:
                    if not addr.old_street:
                        raise
                    choice = await self._pick(step, path, addr.old_street, "street")
            else:
                choice = await self._pick(step, path, addr.city if role == "city" else addr.number, role)
            path = path + (choice.id,)
        return path


async def crawl(crawler: Crawler, buildings: Path, cache_dir: Path, budget_s: float, max_age_days: int) -> dict:
    import aiohttp
    from ua_outages import operator
    from ua_outages.http import CachingFetcher, ResponseCache, Throttle

    from common.http import USER_AGENT

    op = operator(crawler.operator)
    lookup = op.lookup if op else None
    if lookup is None:
        raise SystemExit(f"{crawler.operator}: no address directory")
    if not lookup.steps:
        raise SystemExit(f"{crawler.operator}: directory has no steps")
    cache_path = cache_dir / f"{crawler.id}.json.gz"
    cache = load_cache(cache_path)
    gate = load_cache(cache_dir / f"{crawler.after}.json.gz") if crawler.after else None
    today = date.today()
    stale_before = (today - timedelta(days=max_age_days)).isoformat()

    # Work list: bigger settlements first, so the most people are covered soonest.
    todo: list[Address] = []
    sizes: Counter[str] = Counter()
    seen: set[str] = set()
    for addr, _, _ in read_buildings(buildings, crawler.region):
        if crawler.region != "kyiv-city" and not addr.city:
            continue
        if addr.key in seen:
            continue
        seen.add(addr.key)
        if gate is not None:
            other = gate.get(addr.key)
            if other is None or other.get("g") or other.get("m") not in ("city", "street"):
                continue  # not looked up there yet, or found there
        entry = cache.get(addr.key)
        if entry and entry["t"] >= stale_before and (entry.get("g") or entry.get("v") == MATCHER):
            continue
        todo.append(addr)
        sizes[addr.city] += 1
    todo.sort(key=lambda a: (-sizes[a.city], a.city, a.street, a.number))
    log.info("%s: %d addresses to look up, %d cached", crawler.id, len(todo), len(cache))

    started = time.monotonic()
    stats: Counter[str] = Counter()
    gap = max(MIN_GAP, float(getattr(lookup, "request_gap", 0)))
    async with aiohttp.ClientSession() as session:
        http = CachingFetcher(session, lookup.hosts, cache=ResponseCache(ttl=6 * 3600, max_bytes=64 << 20, max_entries=20_000),
                              throttle=Throttle(gap), timeout=25, user_agent=USER_AGENT)
        walker = Walker(lookup, http)
        dead_streets: dict[tuple[str, str], str] = {}  # (city, street) -> why it was not found
        errors_in_row = 0
        for n, addr in enumerate(todo):
            if time.monotonic() - started > budget_s:
                break
            if (reason := dead_streets.get((addr.city, addr.street))) is not None:
                cache[addr.key] = {"m": reason, "t": today.isoformat(), "v": MATCHER}
                stats[reason] += 1
                continue
            try:
                path = await walker.find(addr)
                walker.requests += 1
                res = await lookup.resolve(http, list(path))
                groups = list(res.groups)
                cache[addr.key] = {"g": groups, "t": today.isoformat()} if groups else {"m": "nogroup", "t": today.isoformat(), "v": MATCHER}
                stats["found" if groups else "nogroup"] += 1
                errors_in_row = 0
            except Miss as miss:
                reason = str(miss)
                cache[addr.key] = {"m": reason, "t": today.isoformat(), "v": MATCHER}
                stats[reason] += 1
                if reason in ("street", "city"):
                    dead_streets[(addr.city, addr.street)] = reason
                errors_in_row = 0
            except ValueError as error:  # the directory contradicts itself: not worth retrying soon
                cache[addr.key] = {"m": "invalid", "t": today.isoformat(), "v": MATCHER}
                stats["invalid"] += 1
                log.debug("%s: %s", addr.key, error)
            except Exception as error:  # network trouble: leave it for the next run
                stats["error"] += 1
                errors_in_row += 1
                log.warning("%s: %s: %s", addr.key, type(error).__name__, str(error)[:160])
                if errors_in_row >= 20:
                    log.error("%s: too many errors in a row, stopping this run", crawler.id)
                    break
                await asyncio.sleep(min(60, 2 ** min(errors_in_row, 6)))
            if n % 500 == 0 and n:
                log.info("%s: %d/%d, %s, %d requests", crawler.id, n, len(todo), dict(stats), walker.requests)
                save_cache(cache_path, cache)
    save_cache(cache_path, cache)
    found = sum(1 for e in cache.values() if e.get("g"))
    summary = {"crawler": crawler.id, "todo": len(todo), "run": dict(stats), "cached": len(cache), "withGroup": found,
               "requests": walker.requests}
    log.info("%s", summary)
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--buildings", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--crawler", required=True, choices=sorted(CRAWLERS))
    parser.add_argument("--budget", type=float, default=300, help="minutes")
    parser.add_argument("--max-age", type=int, default=45, help="days before an address is looked up again")
    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    summary = asyncio.run(crawl(CRAWLERS[args.crawler], args.buildings, args.cache, args.budget * 60, args.max_age))
    print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
