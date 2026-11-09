# Manual schedules

For regions whose source cannot be read automatically (see `../SOURCES.md`),
a schedule can be entered by hand. Create `<region_id>.json` here, for example
`kyiv.json`, and commit it to `main`. The next data run publishes it.

```json
{
  "sourceUrl": "https://www.dtek-krem.com.ua/ua/shutdowns",
  "updatedAt": "2026-11-07T18:00:00+02:00",
  "days": [
    {
      "date": "2026-11-08",
      "groups": {
        "1.1": [{"from": "08:00", "to": "12:00", "type": "off"}],
        "1.2": [{"from": "14:00", "to": "15:30", "type": "maybe"}],
        "2.1": []
      }
    }
  ]
}
```

- Times are local Kyiv time, intervals are `[from, to)`, `"24:00"` is allowed.
- `type` is `off` (no power) or `maybe` (possible outage). Uncovered time means power is on.
- List every group of the region on each day, with `[]` for groups without outages.
- Days before today are ignored. When no day is left, the region is shown as outdated.
- For a region without an automatic provider, the file is the region's source.
- For a region with an automatic provider, the file is a fallback: it is used only while the
  source has no schedule (it fails, says schedules do not apply, or reports emergency outages
  without hours) and the file still has a day from today on. A real schedule from the source
  always wins.

## From a saved DTEK page

DTEK sites are often behind bot protection, but they open fine in a normal browser. Open the
shutdowns page, save it with Ctrl+S ("Webpage, HTML only") and run from `data/`:

```sh
python tools/manual_from_dtek.py page.html dnipropetrovsk
```

This writes `manual/dnipropetrovsk.json` with every day the page has. Commit and push it.
- The file is validated on every run; an invalid file never replaces published data.
