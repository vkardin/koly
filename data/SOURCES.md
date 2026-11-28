# Data sources

Schedules come from public pages and APIs of the distribution system operators.
Clients never call these sources directly; they read only the published JSON.

Status legend: **done** — automatic parser; **manual** — no usable automatic source, a schedule can be
entered by hand in `manual/<region_id>.json` (see [manual/README.md](manual/README.md));
**todo** — source not found or not machine-readable yet; **none** — the operator publishes no schedule a polite client can read.

Some regions are read through the open library `ukraine-energy-monitor` (MIT, pinned to a commit in `requirements.txt`) and open mirrors on GitHub (`providers/uamonitor.py`, `providers/_mirror.py`). A mirror is someone else's copy: it is used only where the operator's own source cannot be read and is checked for freshness.

Sources behind bot challenges or answering 403 to non-browser clients are not worked around:
the project respects the sources' access rules. Such regions use manual files or wait for an open source.

Every region is also read from the other sources listed for it (another parser of the operator's data, open mirrors) and compared group by group. The result is published in the region file as `checks`: `same`, `different` (with the other source's hours), `older` (the other source has not caught up with the main one) or `failed`. If the main source fails, the first other source that works is published instead.

The CEK part of Dnipropetrovsk is published as its own region, `dnipropetrovsk-cek` (index field `area: "dnipropetrovsk"`).

Last checked: 2026-11-26.

| Region (id) | Provider | URL | Format | Parser | Notes |
|---|---|---|---|---|---|
| Kyiv city (`kyiv-city`) | DTEK KEM (`dtek-kem`) | https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/25/dsos/902/planned-outages | API (JSON) | done | dtek-kem.com.ua itself is behind a bot challenge; the public YASNO API serves the same groups (`1.1` … `60.1`). |
| Dnipropetrovsk (`dnipropetrovsk`) | DTEK DnEM (`dtek-dnem`) | https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/3/dsos/301/planned-outages | API (JSON) | done | The CEK part of the region is published separately (next row). When YASNO reports emergency outages without hours, `manual/dnipropetrovsk.json` (made from the DTEK site with `tools/manual_from_dtek.py`) is used if it is current. |
| Dnipropetrovsk, CEK (`dnipropetrovsk-cek`) | CEK (`cek`) | https://cek.dp.ua | API (JSON) | done | library [ukraine-energy-monitor](https://github.com/fastiuk/ukraine-energy-monitor): YASNO `dsos/303`, then the DTEK page and the mirror. |
| Odesa (`odesa`) | DTEK OEM (`dtek-oem`) | https://www.dtek-oem.com.ua/ua/shutdowns | JSON in page (`DisconSchedule.fact`) | done | Sometimes answers with a bot challenge; the region then stays `stale` until the next good answer. |
| Lviv (`lviv`) | Lvivoblenergo (`lvivoblenergo`) | https://api.loe.lviv.ua/api/menus?page=1&type=photo-grafic | API (JSON with HTML text) | done | Response is about 2 MB, fetched at most every 30 min. No item for today means schedules are not applied (`no_schedules`). |
| Khmelnytskyi (`khmelnytskyi`) | Khmelnytskoblenergo (`khmelnytskoblenergo`) | https://hoe.com.ua/page/pogodinni-vidkljuchennja | HTML text | done | "Підчерга 1.1 – 00:00-04:00; …". No timestamp at the source. When the source fails or does not answer GitHub's servers, the mirror [OE_OUTAGE_DATA](https://github.com/yaroslav2901/OE_OUTAGE_DATA) is used. |
| Zaporizhzhia (`zaporizhzhia`) | Zaporizhzhiaoblenergo (`zaporizhzhiaoblenergo`) | https://www.zoe.com.ua/outage/ | HTML news posts | done | Newest post per date wins, including "ОНОВЛЕНО ГПВ" updates. Hours already include 30 min for switching. When the source fails or does not answer GitHub's servers, the mirror [OE_OUTAGE_DATA](https://github.com/yaroslav2901/OE_OUTAGE_DATA) is used. |
| Zhytomyr (`zhytomyr`) | Zhytomyroblenergo (`zhytomyroblenergo`) | https://www.ztoe.com.ua/unhooking-search.php | HTML table (half-hour cells, windows-1251) | done | Red cells mean no power. |
| Rivne (`rivne`) | Rivneoblenergo (`rivneoblenergo`) | https://www.roe.vsei.ua/disconnections | HTML table | done | Cells say "Очікується" until hours are published; the region appears after the first published day. |
| Cherkasy (`cherkasy`) | Cherkasyoblenergo (`cherkasyoblenergo`) | https://www.cherkasyoblenergo.com/ | HTML news posts | done | Home page lists posts; the newest post per date is fetched (at most 3 requests). |
| Mykolaiv (`mykolaiv`) | Mykolaivoblenergo (`mykolaivoblenergo`) | https://off.energy.mk.ua/api/v2/schedule/active | API (JSON) | done | Half-hour slots, `OFF` and `PROBABLY_OFF`. |
| Chernivtsi (`chernivtsi`) | Chernivtsioblenergo (`chernivtsioblenergo`) | https://oblenergo.cv.ua/shutdowns/ | HTML grid (half-hour cells) | done | Rows `inf1`…`inf12`, letters з (on) / в (off) / мз (maybe powered, published as `maybe`). Answers 403 to GitHub's servers (2026-11-16) and no mirror carries it: the region is published only when the site lets the run in. |
| Kyiv region (`kyiv`) | DTEK KREM (`dtek-krem`) | https://www.dtek-krem.com.ua/ua/shutdowns | Mirror (JSON) | done | The site is behind a bot challenge, so the open mirror [outage-data-ua](https://github.com/Baskerville42/outage-data-ua) (MIT) is read; it must have refreshed within 3 hours. |
| Donetsk (`donetsk`) | DTEK DEM | https://www.dtek-dem.com.ua/ua/shutdowns | — | none | No schedule published since 12.2024. |
| Vinnytsia (`vinnytsia`) | Vinnytsiaoblenergo | https://disconnection.voe.com.ua/ | Mirror (JSON per group) | done | The operator's own site is behind a Cloudflare challenge, so the schedule is read from [Svitlobot timetables](https://github.com/Svitlobot-official/timetables) (CC BY 4.0), which carries it. No second source to check against. |
| Ternopil (`ternopil`) | Ternopiloblenergo (`ternopiloblenergo`) | https://poweron.toe.com.ua | Mirror (JSON) | done | The operator gives hours only by address, so the mirror [OE_OUTAGE_DATA](https://github.com/yaroslav2901/OE_OUTAGE_DATA) is read. |
| Kirovohrad (`kirovohrad`) | Kirovohradoblenergo (`kirovohradoblenergo`) | https://kiroe.com.ua/electricity-blackout | Text posts in the operator's official Telegram channels | done | library [ukraine-energy-monitor](https://github.com/fastiuk/ukraine-energy-monitor) (public preview `t.me/s/SvitloKropyvnytskyiMisto`). The site's own data is behind a bot challenge. |
| Ivano-Frankivsk (`ivano-frankivsk`) | Prykarpattiaoblenergo (`prykarpattiaoblenergo`) | https://svitlo.oe.if.ua | API (JSON per queue) | done | library [ukraine-energy-monitor](https://github.com/fastiuk/ukraine-energy-monitor), then mirror [OE_OUTAGE_DATA](https://github.com/yaroslav2901/OE_OUTAGE_DATA). |
| Poltava (`poltava`) | Poltavaoblenergo (`poltavaoblenergo`) | https://www.poe.pl.ua/disconnection/power-outages/ | HTML table (half-hour cells) | done | library [ukraine-energy-monitor](https://github.com/fastiuk/ukraine-energy-monitor); the site does not answer GitHub's servers, then the mirror [OE_OUTAGE_DATA](https://github.com/yaroslav2901/OE_OUTAGE_DATA) is used. |
| Kharkiv (`kharkiv`) | Kharkivoblenergo (`kharkivoblenergo`) | https://oblenergo.kharkiv.ua/uk/media_centre/news | RSS with full text | done | library [ukraine-energy-monitor](https://github.com/fastiuk/ukraine-energy-monitor); the site does not answer GitHub's servers, then the mirror [OE_OUTAGE_DATA](https://github.com/yaroslav2901/OE_OUTAGE_DATA) is used. |
| Volyn (`volyn`) | Volynoblenergo (`volynoblenergo`) | https://energy.volyn.ua/ | Picture (PNG grid) from the operator's API | done | Read by the library [ukraine-energy-monitor](https://github.com/fastiuk/ukraine-energy-monitor). |
| Zakarpattia (`zakarpattia`) | Zakarpattiaoblenergo (`zakarpattiaoblenergo`) | https://zakarpat.energy/customers/break-in-electricity-supply/schedule/ | Operator's API | done | library [ukraine-energy-monitor](https://github.com/fastiuk/ukraine-energy-monitor), then mirror [OE_OUTAGE_DATA](https://github.com/yaroslav2901/OE_OUTAGE_DATA). No picture published means schedules are not applied (`no_schedules`). |
| Sumy (`sumy`) | Sumyoblenergo (`sumyoblenergo`) | https://www.soe.com.ua/consumers/shutdowns | Picture in the operator's news | done | library [ukraine-energy-monitor](https://github.com/fastiuk/ukraine-energy-monitor) reads the table from the picture and checks it against itself; a day with unread hours is not published. |
| Kherson (`kherson`) | Khersonoblenergo | https://www.ksoe.com.ua/ | — | none | Schedules only in the personal cabinet. |
| Chernihiv (`chernihiv`) | Chernihivoblenergo (`chernihivoblenergo`) | https://chernihivoblenergo.com.ua/blackouts | Mirror (JSON) | done | The operator publishes hours only as a picture (its API is behind Turnstile), so the mirror [OE_OUTAGE_DATA](https://github.com/yaroslav2901/OE_OUTAGE_DATA) is read. |

## Adding a provider

1. Save a real response to `tests/fixtures/<provider_module>.<ext>`.
2. Write `providers/<provider_module>.py` with `id`, `region_ids`, `source_url` and `fetch()`.
   Put parsing in a pure function that takes the raw response (and today's date), so it can be tested.
3. Raise `ProviderError` on anything unexpected (unknown values, empty data) and `NotPublished` when the
   source works but has nothing for today. The pipeline then keeps the last valid data and marks the
   region `stale` instead of publishing a wrong schedule.
4. Register it in `providers/__init__.py` and add tests. Remove the manual file for that region, if any.

## Monitoring

`monitor.py` runs after every data run. When a region stays `stale` for more than 6 hours
(`KOLY_STALE_ALERT_HOURS`), it opens an issue labelled `data-alert` and closes it when the region
recovers. A failed workflow run opens a separate issue that closes after the next successful run.

## Being polite to sources

- One request per provider every 10 minutes (the workflow schedule), retries only on errors;
  heavy responses less often (`min_interval`).
- User-Agent `KolyYe/<version> (+https://vkardin.github.io)`.
- Published files change only when the schedule changes, so clients can use ETag caching.

## House map

`houses/` builds the house layer of the map (`v1/houses/`), run by `.github/workflows/houses.yml`:

1. `houses/osm.py`: every building with a street and number from the OpenStreetMap extract of Ukraine (Geofabrik), with its settlement (`addr:city`, else the settlement area it is in, else a clearly nearest settlement point), and the streets around them. Temporarily occupied territory is left out.
2. `houses/crawl.py`: the group of each address from the operators' own address directories (YASNO for Kyiv city and Dnipropetrovsk with CEK, Lviv, Ternopil, Khmelnytskyi, Mykolaiv, Zhytomyr), read through `ukraine-energy-monitor`. One request at a time, at least a second apart, every answer cached on the `houses-cache` branch; each address is looked up again after about six weeks. An address gets a group only when its settlement, street and number match exactly one directory entry.
3. `houses/tiles.py`: tiles of about 11 km with the buildings, their groups and the streets. Clients colour each building by its group's schedule right now.

Regions without an open address directory still show their buildings, as "group unknown".

Map data © OpenStreetMap contributors, [ODbL](https://opendatacommons.org/licenses/odbl/); the published house tiles are under the same licence.

## Extra checks: Svitlobot

For most regions the [Svitlobot timetables](https://github.com/Svitlobot-official/timetables) (CC BY 4.0, one file per group and week) are one more independent source in `checks`. Only the hours are compared with the main source, not whether an interval is an outage or a possible outage, because Svitlobot does not make that distinction. A day it has not published (`outages: null`) counts as "not published", not as "no outages".
