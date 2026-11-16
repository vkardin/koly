# Data sources

Schedules come from public pages and APIs of the distribution system operators.
Clients never call these sources directly; they read only the published JSON.

Status legend: **done** — automatic parser; **manual** — no usable automatic source, a schedule can be
entered by hand in `manual/<region_id>.json` (see [manual/README.md](manual/README.md));
**todo** — source not found or not machine-readable yet.

Sources behind bot challenges or answering 403 to non-browser clients are not worked around:
the project respects the sources' access rules. Such regions use manual files or wait for an open source.

Last checked: 2026-11-16.

| Region (id) | Provider | URL | Format | Parser | Notes |
|---|---|---|---|---|---|
| Kyiv city (`kyiv-city`) | DTEK KEM (`dtek-kem`) | https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/25/dsos/902/planned-outages | API (JSON) | done | dtek-kem.com.ua itself is behind a bot challenge; the public YASNO API serves the same groups (`1.1` … `60.1`). |
| Dnipropetrovsk (`dnipropetrovsk`) | DTEK DnEM (`dtek-dnem`) | https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/3/dsos/301/planned-outages | API (JSON) | done | Part of the region is served by CEK (same API, `dsos/303`) with its own groups; not published yet, needs a schema decision. When YASNO reports emergency outages without hours, `manual/dnipropetrovsk.json` (made from the DTEK site with `tools/manual_from_dtek.py`) is used if it is current. |
| Odesa (`odesa`) | DTEK OEM (`dtek-oem`) | https://www.dtek-oem.com.ua/ua/shutdowns | JSON in page (`DisconSchedule.fact`) | done | Sometimes answers with a bot challenge; the region then stays `stale` until the next good answer. |
| Lviv (`lviv`) | Lvivoblenergo (`lvivoblenergo`) | https://api.loe.lviv.ua/api/menus?page=1&type=photo-grafic | API (JSON with HTML text) | done | Response is about 2 MB, fetched at most every 30 min. No item for today means schedules are not applied (`no_schedules`). |
| Khmelnytskyi (`khmelnytskyi`) | Khmelnytskoblenergo (`khmelnytskoblenergo`) | https://hoe.com.ua/page/pogodinni-vidkljuchennja | HTML text | done | "Підчерга 1.1 – 00:00-04:00; …". No timestamp at the source. |
| Zaporizhzhia (`zaporizhzhia`) | Zaporizhzhiaoblenergo (`zaporizhzhiaoblenergo`) | https://www.zoe.com.ua/outage/ | HTML news posts | done | Newest post per date wins, including "ОНОВЛЕНО ГПВ" updates. Hours already include 30 min for switching. |
| Zhytomyr (`zhytomyr`) | Zhytomyroblenergo (`zhytomyroblenergo`) | https://www.ztoe.com.ua/unhooking-search.php | HTML table (half-hour cells, windows-1251) | done | Red cells mean no power. |
| Rivne (`rivne`) | Rivneoblenergo (`rivneoblenergo`) | https://www.roe.vsei.ua/disconnections | HTML table | done | Cells say "Очікується" until hours are published; the region appears after the first published day. |
| Cherkasy (`cherkasy`) | Cherkasyoblenergo (`cherkasyoblenergo`) | https://www.cherkasyoblenergo.com/ | HTML news posts | done | Home page lists posts; the newest post per date is fetched (at most 3 requests). |
| Mykolaiv (`mykolaiv`) | Mykolaivoblenergo (`mykolaivoblenergo`) | https://off.energy.mk.ua/api/v2/schedule/active | API (JSON) | done | Half-hour slots, `OFF` and `PROBABLY_OFF`. |
| Chernivtsi (`chernivtsi`) | Chernivtsioblenergo (`chernivtsioblenergo`) | https://oblenergo.cv.ua/shutdowns/ | HTML grid (half-hour cells) | done | Rows `inf1`…`inf12`, letters з (on) / в (off) / мз (maybe powered, published as `maybe`). |
| Kyiv region (`kyiv`) | DTEK KREM | https://www.dtek-krem.com.ua/ua/shutdowns | JSON in page | manual | Bot challenge (Incapsula, checked 2026-11-16). The YASNO API does not list this region. |
| Donetsk (`donetsk`) | DTEK DEM | https://www.dtek-dem.com.ua/ua/shutdowns | JSON in page | manual | Bot challenge (Incapsula, checked 2026-11-16). |
| Vinnytsia (`vinnytsia`) | Vinnytsiaoblenergo | https://www.voe.com.ua/disconnection/detailed | HTML | manual | HTTP 403 for non-browser clients. |
| Ternopil (`ternopil`) | Ternopiloblenergo | https://www.toe.com.ua | HTML | manual | HTTP 403 for non-browser clients. |
| Kirovohrad (`kirovohrad`) | Kirovohradoblenergo | https://kiroe.com.ua/ | HTML news posts | manual | Posts give only the number of queues per period ("з 08:00 до 22:00 – 3 черги"), not hours per group. |
| Ivano-Frankivsk (`ivano-frankivsk`) | Prykarpattiaoblenergo | https://oe.if.ua/uk/shutdowns_table | Address search | manual | Address search only; be-svitlo.oe.if.ua answers 403 to non-browser clients. |
| Poltava (`poltava`) | Poltavaoblenergo | https://www.poe.pl.ua/disconnection/hrafik-pohodynnoho-vidkliuchennia-elektroenerhii/ | PDF | manual | Half-year PDF (6 queues with sub-queues, 01.10.2026 – 01.04.2027); daily hours are not published by group. |
| Kharkiv (`kharkiv`) | Kharkivoblenergo | https://oblenergo.kharkiv.ua/uk | HTML news | manual | News say when schedules apply but not the hours per group. Answers 400 to non-browser clients (2026-11-16). |
| Volyn (`volyn`) | Volynoblenergo | https://energy.volyn.ua/ | HTML form (POST) | manual | Hours for today and tomorrow only by address (form with date, city and street), not by group. |
| Zakarpattia (`zakarpattia`) | Zakarpattiaoblenergo | https://zakarpat.energy/customers/break-in-electricity-supply/schedule/ | PDF per sub-queue | manual | Hours per sub-queue only in yearly PDFs (gpv1-1 … gpv6-2). The inneti API behind the site maps addresses to queues and lists outages, but has no hours per group. |
| Sumy (`sumy`) | Sumyoblenergo | https://www.soe.com.ua/consumers/shutdowns | JS app (Next.js) | todo | Page loads, but the schedule is fetched by scripts; no documented endpoint found. |
| Kherson (`kherson`) | Khersonoblenergo | https://www.ksoe.com.ua/ | — | todo | Site did not respond (2026-11-07, 2026-11-16). |
| Chernihiv (`chernihiv`) | Chernihivoblenergo | https://www.energy.cn.ua/ | — | todo | Site did not respond (2026-11-07, 2026-11-16). |

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
