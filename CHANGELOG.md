# Changelog

## Unreleased

- New "Detailed" layout in the Android app, the Windows widget and on the website: every outage of today and tomorrow with its length and whether it is over, going on or next; totals for the day; the longest stretch with power; the operator's notice; and where the schedule comes from and how the other sources compare.
- One more independent source for most regions (Svitlobot timetables), and Vinnytsia is back, read from it.
- Khmelnytskyi: "additional hours" posts are added to the day's schedule instead of failing the region.
- A mirror's update time that lies in the future is replaced by the time the mirror was last refreshed.
- Map: lighter colour for buildings whose group is not known yet, and no more console warnings about missing icons.

## android 1.6.0 — 2026-11-26

- New map: a real map with streets and place names under the regions and buildings, smooth pinch-zoom and dragging on the graphics chip (MapLibre with OpenFreeMap tiles). Light and dark to match the app.
- Buildings are filled by their own outline: yellow with power, grey without, orange when an outage is possible, dark while the group is unknown. From town level they show as dots; tap one to see its group and hours, or add it to your places.
- Where the groups of buildings are still being collected, the map says so and how much is known.

## desktop 1.6.0 — 2026-11-26

- New map with streets and place names, smooth zoom and dragging; follows the light or dark theme.

## android 1.5.1 — 2026-11-26

- The operator's notice, word for word, on the home card and the map (for example, that emergency outages replace the schedule in part of the region).
- DTEK regions (Kyiv, Kyiv region, Dnipropetrovsk with CEK, Odesa): the weekly schedule of possible outages for your group, next to the schedule in force.

## desktop 1.5.1 — 2026-11-26

- Map: the operator's notice and the weekly schedule of possible outages in DTEK regions.

## android 1.5.0 — 2026-11-26

- Map: "Where am I" button. Asks for your approximate location once and zooms to your street; without it, goes to the region of your main place.
- Map: search by street and house number, with suggestions; picks the building and shows its group.
- Map: "Add to my places" on a building. The place gets its region and group from the building, so you no longer need to know your group.
- Map: time slider. See where the power will be on later today and tomorrow, in half-hour steps.
- The Statistics tab is gone.

## desktop 1.5.0 — 2026-11-26

- Map: "Where am I", search by street and house number, "Add to my places" on a building, and a time slider for later today and tomorrow.

## android 1.2.0 — 2026-11-26

- Buildings on the map: zoom in on a town and every addressed building is drawn, coloured by its group's schedule right now (power on, possible outage, no power, group unknown). Tap a building for its address, group, today's hours and when the power changes next. Zoomed out, the map shows regions as before.
- Schedules are checked against other sources: where they agree the app says so; where another source shows other hours, both are shown with the source and its update time.
- Dnipropetrovsk: the part served by CEK, with its own groups.
- Built-in updates: the app checks its GitHub releases, downloads a new version, checks it and hands it to the system installer.

## desktop 1.2.0 — 2026-11-26

- Buildings on the map: zoom in on a town to see every addressed building coloured by its group's schedule; click one for its address, group and today's hours.
- Schedules are checked against other sources; differences are shown with the source.
- Dnipropetrovsk: the part served by CEK, with its own groups.

## android 1.1.0 — 2026-11-16

- Power map: a new Map tab with every region coloured by the share of groups with power right now. Pinch to zoom, drag to move, tap a region for its groups.
- Eleven more regions: Kyiv region, Chernivtsi, Volyn, Zakarpattia, Ivano-Frankivsk, Kirovohrad, Poltava, Sumy, Ternopil, Kharkiv and Chernihiv.

## desktop 1.1.0 — 2026-11-16

- Power map: a new section in settings, also in the right-click menu and the tray. Every region is coloured by the share of groups with power right now; zoom with the wheel or buttons, drag to move, click a region for its groups and the next change.
- Eleven more regions: Kyiv region, Chernivtsi, Volyn, Zakarpattia, Ivano-Frankivsk, Kirovohrad, Poltava, Sumy, Ternopil, Kharkiv and Chernihiv.
- Ring and strip no longer show "power on" when there is no schedule for today.
- Strip and ring show emergency, offline, outdated and demo warnings; ring gets refresh and settings buttons on hover, strip gets a settings button.
- Countdowns show the time of the next change.
- Settings: keyboard focus survives every change, arrow keys work in option groups, places can be reordered with Alt+Up/Down, removing a place can be undone.
- Screen reader announcements for reordering, removal and update checks; reduced motion support.
- Right-click on the widget opens a menu: refresh, view, window mode, lock position, settings, hide.
- Tooltips and screen readers read countdowns as a full sentence ("2 год 14 хв"), not as a clock time.
- Countdowns update exactly on the minute. Auto-refresh catches up after sleep, retries every 2 minutes while offline and refreshes as soon as the network is back.
- First launch opens settings on Places.
- Settings: test notification reports when Windows blocks notifications; "Refresh now" shows progress; lead time is disabled while both warnings are off.
- Better contrast for "possible outage" text, danger buttons and focus outlines in the light theme.

## android 1.0.0 — 2026-11-08

First release of the Android app.

- Onboarding: language, region, group, home screen view, notifications with exact timing, disclaimer.
- Home screen in three views: card and bar, day ring, all places as a list.
- Up to 10 places, statistics for 7 days, settings, uk/en/ru.
- Offline cache with conditional requests and periodic sync.
- Notifications before outages and before power returns, on schedule changes and when tomorrow is published.
- Home screen widgets: Cards (one place, several places) and Minimal (ring, pill, text). One layout scaled to any size, outage list with durations and total, countdown refreshed every minute, a mark when data is out of date, dark, light or system theme.

## desktop 1.0.0 — 2026-11-07

First stable release of the Windows widget.

- Three views: Strip, Card and Ring.
- Tray menu, always-on-top or on-desktop modes, remembered position.
- Settings window: appearance, up to 10 places, notifications, language (uk/en/ru), autostart.
- Reads the published schedules, keeps a disk cache and works offline.
- Notifications before outages, before power returns, on schedule changes and when tomorrow is published.
- Built-in auto-update.

## desktop 0.1.0 — 2026-11-07

- Preview build of the Windows widget.
