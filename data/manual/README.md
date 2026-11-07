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
- A file is used only while no automatic provider covers the region.
- The file is validated on every run; an invalid file never replaces published data.
