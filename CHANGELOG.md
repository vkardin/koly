# Changelog

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
