# Data sources

Schedules come from public pages and APIs of the distribution system operators.
Clients never call these sources directly; they read only the published JSON.

Status legend: **done** — parser in production; **next** — source checked, parser not written yet;
**todo** — source not researched in detail yet; **blocked** — source is reachable only through a bot challenge.

Last checked: 2026-11-07.

| Region (id) | Provider | URL | Format | Parser | Notes |
|---|---|---|---|---|---|
| Kyiv city (`kyiv-city`) | DTEK KEM (`dtek-kem`) | https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/25/dsos/902/planned-outages | API (JSON) | done | Site dtek-kem.com.ua is behind a bot challenge; the public YASNO API serves the same groups. 60 groups (`1.1` … `60.1`). |
| Dnipropetrovsk (`dnipropetrovsk`) | DTEK DnEM (`dtek-dnem`) | https://app.yasno.ua/api/blackout-service/public/shutdowns/regions/3/dsos/301/planned-outages | API (JSON) | done | Part of the region is served by CEK (same API, `dsos/303`) with its own groups; not published yet, needs a separate region entry or a schema decision. |
| Odesa (`odesa`) | DTEK OEM (`dtek-oem`) | https://www.dtek-oem.com.ua/ua/shutdowns | JSON in page (`DisconSchedule.fact`) | done | Hourly values with half-hour precision (`first` / `second`). Emergency notices are not parsed yet. |
| Kyiv region (`kyiv`) | DTEK KREM | https://www.dtek-krem.com.ua/ua/shutdowns | JSON in page | blocked | Bot challenge for non-browser clients. Same page format as Odesa. YASNO API does not list this region. |
| Donetsk (`donetsk`) | DTEK DEM | https://www.dtek-dem.com.ua/ua/shutdowns | JSON in page | blocked | Bot challenge, same as KREM. |
| Vinnytsia (`vinnytsia`) | Vinnytsiaoblenergo | https://www.voe.com.ua/disconnection/detailed | HTML | blocked | HTTP 403 for non-browser clients. |
| Volyn (`volyn`) | Volynoblenergo | https://energy.volyn.ua | HTML | todo | Schedule page URL to be confirmed. |
| Zhytomyr (`zhytomyr`) | Zhytomyroblenergo | https://www.ztoe.com.ua/unhooking-search.php | HTML | next | Page reachable. |
| Zakarpattia (`zakarpattia`) | Zakarpattiaoblenergo | https://zakarpat.energy/customers/break-in-electricity-supply/schedule/ | HTML / image | next | Page reachable; format to be confirmed. |
| Zaporizhzhia (`zaporizhzhia`) | Zaporizhzhiaoblenergo | https://www.zoe.com.ua/outage/ | HTML | next | Page reachable. |
| Ivano-Frankivsk (`ivano-frankivsk`) | Prykarpattiaoblenergo | https://www.oe.if.ua | HTML | todo | Schedule page URL to be confirmed. |
| Kirovohrad (`kirovohrad`) | Kirovohradoblenergo | https://kiroe.com.ua | HTML | todo | Schedule page URL to be confirmed. |
| Lviv (`lviv`) | Lvivoblenergo | https://poweron.loe.lviv.ua/ | API behind a JS app | next | The page is a single-page app; find the JSON endpoint it uses. |
| Mykolaiv (`mykolaiv`) | Mykolaivoblenergo | https://www.energy.mk.ua | HTML | todo | Schedule page URL to be confirmed. |
| Poltava (`poltava`) | Poltavaoblenergo | https://www.poe.pl.ua/disconnection/power-outages/ | HTML | next | Page reachable. |
| Rivne (`rivne`) | Rivneoblenergo | https://www.roe.vsei.ua/disconnections | HTML | next | Page reachable. |
| Sumy (`sumy`) | Sumyoblenergo | https://www.soe.com.ua | HTML | todo | Schedule page URL to be confirmed. |
| Ternopil (`ternopil`) | Ternopiloblenergo | https://www.toe.com.ua | HTML | blocked | HTTP 403 for non-browser clients. |
| Kharkiv (`kharkiv`) | Kharkivoblenergo | https://www.oblenergo.kharkov.ua | HTML / image | todo | Schedule page URL to be confirmed. |
| Kherson (`kherson`) | Khersonoblenergo | https://www.ksoe.com.ua | HTML | todo | Site did not respond during the check. |
| Khmelnytskyi (`khmelnytskyi`) | Khmelnytskoblenergo | https://hoe.com.ua/page/pogodinni-vidkljuchennja | HTML | next | Page reachable. |
| Cherkasy (`cherkasy`) | Cherkasyoblenergo | https://www.cherkasyoblenergo.com | HTML / Telegram | todo | Schedule page URL to be confirmed. |
| Chernivtsi (`chernivtsi`) | Chernivtsioblenergo | https://oblenergo.cv.ua/shutdowns/ | HTML | next | Page reachable. |
| Chernihiv (`chernihiv`) | Chernihivoblenergo | https://www.energy.cn.ua | HTML | todo | Site did not respond during the check. |

## Adding a provider

1. Save a real response to `tests/fixtures/<provider_module>.<ext>`.
2. Write `providers/<provider_module>.py` with `id`, `region_ids`, `source_url` and `fetch()`.
   Put parsing in a pure function that takes the raw response, so it can be tested on the fixture.
3. Raise `ProviderError` on anything unexpected (unknown values, empty data). The pipeline then
   keeps the last valid data and marks the region `stale` instead of publishing a wrong schedule.
4. Register it in `providers/__init__.py` and add tests.

## Being polite to sources

- One request per provider every 10 minutes (the workflow schedule), with retries only on errors.
- User-Agent `KolyYe/<version> (+https://vkardin.github.io)`.
- Published files change only when the schedule changes, so clients can use ETag caching.
