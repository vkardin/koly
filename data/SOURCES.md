# Data sources

Schedules come from public pages and APIs of the distribution system operators.
Clients never call these sources directly; they read only the published JSON.

Status legend: **done** — automatic parser; **manual** — no usable automatic source, a schedule can be
entered by hand in `manual/<region_id>.json` (see [manual/README.md](manual/README.md));
**todo** — source not found or not machine-readable yet.

Sources behind bot challenges or answering 403 to non-browser clients are not worked around:
the project respects the sources' access rules. Such regions use manual files or wait for an open source.

Last checked: 2026-11-07.

| Region (id) | Provider | URL | Format | Parser | Notes |
|---|---|---|---|---|---|
| Kyiv city (`kyiv-city`) | DTEK KEM (`dtek-kem`) | https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/25/dsos/902/planned-outages | API (JSON) | done | dtek-kem.com.ua itself is behind a bot challenge; the public YASNO API serves the same groups (`1.1` … `60.1`). |
| Dnipropetrovsk (`dnipropetrovsk`) | DTEK DnEM (`dtek-dnem`) | https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/3/dsos/301/planned-outages | API (JSON) | done | Part of the region is served by CEK (same API, `dsos/303`) with its own groups; not published yet, needs a schema decision. |
| Odesa (`odesa`) | DTEK OEM (`dtek-oem`) | https://www.dtek-oem.com.ua/ua/shutdowns | JSON in page (`DisconSchedule.fact`) | done | Sometimes answers with a bot challenge; the region then stays `stale` until the next good answer. |
| Lviv (`lviv`) | Lvivoblenergo (`lvivoblenergo`) | https://api.loe.lviv.ua/api/menus?page=1&type=photo-grafic | API (JSON with HTML text) | done | Response is about 2 MB, fetched at most every 30 min. No item for today means schedules are not applied (`no_schedules`). |
| Khmelnytskyi (`khmelnytskyi`) | Khmelnytskoblenergo (`khmelnytskoblenergo`) | https://hoe.com.ua/page/pogodinni-vidkljuchennja | HTML text | done | "Підчерга 1.1 – 00:00-04:00; …". No timestamp at the source. |
| Zaporizhzhia (`zaporizhzhia`) | Zaporizhzhiaoblenergo (`zaporizhzhiaoblenergo`) | https://www.zoe.com.ua/outage/ | HTML news posts | done | Newest post per date wins, including "ОНОВЛЕНО ГПВ" updates. Hours already include 30 min for switching. |
| Zhytomyr (`zhytomyr`) | Zhytomyroblenergo (`zhytomyroblenergo`) | https://www.ztoe.com.ua/unhooking-search.php | HTML table (half-hour cells, windows-1251) | done | Red cells mean no power. |
| Rivne (`rivne`) | Rivneoblenergo (`rivneoblenergo`) | https://www.roe.vsei.ua/disconnections | HTML table | done | Cells say "Очікується" until hours are published; the region appears after the first published day. |
| Cherkasy (`cherkasy`) | Cherkasyoblenergo (`cherkasyoblenergo`) | https://www.cherkasyoblenergo.com/ | HTML news posts | done | Home page lists posts; the newest post per date is fetched (at most 3 requests). |
| Mykolaiv (`mykolaiv`) | Mykolaivoblenergo (`mykolaivoblenergo`) | https://off.energy.mk.ua/api/v2/schedule/active | API (JSON) | done | Half-hour slots, `OFF` and `PROBABLY_OFF`. |
| Kyiv region (`kyiv`) | DTEK KREM | https://www.dtek-krem.com.ua/ua/shutdowns | JSON in page | manual | Bot challenge. The YASNO API does not list this region. |
| Donetsk (`donetsk`) | DTEK DEM | https://www.dtek-dem.com.ua/ua/shutdowns | JSON in page | manual | Bot challenge. |
| Vinnytsia (`vinnytsia`) | Vinnytsiaoblenergo | https://www.voe.com.ua/disconnection/detailed | HTML | manual | HTTP 403 for non-browser clients. |
| Ternopil (`ternopil`) | Ternopiloblenergo | https://www.toe.com.ua | HTML | manual | HTTP 403 for non-browser clients. |
| Kirovohrad (`kirovohrad`) | Kirovohradoblenergo | https://kiroe.com.ua/ | HTML news posts | manual | Posts give only the number of queues per period ("з 08:00 до 22:00 – 3 черги"), not hours per group. |
| Ivano-Frankivsk (`ivano-frankivsk`) | Prykarpattiaoblenergo | https://oe.if.ua/uk/shutdowns_table | Address search | manual | Only an address/account search; no schedule by group found. |
| Poltava (`poltava`) | Poltavaoblenergo | https://www.poe.pl.ua/disconnection/hrafik-pohodynnoho-vidkliuchennia-elektroenerhii/ | Document + address search | manual | Half-year schedule document; daily hours are not published by group. |
| Chernivtsi (`chernivtsi`) | Chernivtsioblenergo | https://oblenergo.cv.ua/shutdowns/ | JS app (eSvitlo) | todo | Data is loaded by scripts; find a documented endpoint. |
| Kharkiv (`kharkiv`) | Kharkivoblenergo | https://oblenergo.kharkiv.ua/uk | HTML news | manual | News say when schedules apply but not the hours per group. |
| Volyn (`volyn`) | Volynoblenergo | https://energy.volyn.ua/ | Images / JS app | manual | Schedule published as images. |
| Zakarpattia (`zakarpattia`) | Zakarpattiaoblenergo | https://zakarpat.energy/customers/break-in-electricity-supply/schedule/ | PDF per group | manual | PDFs list addresses of groups; current hours link was empty during the check. |
| Sumy (`sumy`) | Sumyoblenergo | https://www.soe.com.ua/ | — | todo | Schedule page not found. |
| Kherson (`kherson`) | Khersonoblenergo | https://www.ksoe.com.ua/ | — | todo | Site did not respond during the check. |
| Chernihiv (`chernihiv`) | Chernihivoblenergo | https://www.energy.cn.ua/ | — | todo | Site did not respond during the check. |

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
