# Changelog

## android 1.0.0 — unreleased

First release of the Android app.

- Onboarding: language, region, group, home screen view, notifications with exact timing, disclaimer.
- Home screen in three views: card and bar, day ring, all places as a list.
- Up to 10 places, statistics for 7 days, settings, uk/en/ru.
- Offline cache with conditional requests and periodic sync.
- Notifications before outages and before power returns, on schedule changes and when tomorrow is published.
- Home screen widgets: Cards (one place, several places) and Minimal (ring, pill, text). One layout scaled to any size, outage list with durations and total, countdown refreshed every minute, a mark when data is out of date, dark, light or system theme.

## desktop 1.1.0 — unreleased

- Right-click on the widget opens a menu: refresh, view, window mode, lock position, settings, hide.
- Countdowns show when the state ends ("до 08:00"), and tooltips and screen readers get the full sentence, so "12:24" no longer reads like a clock time.
- Strip and Ring show when data is offline, outdated or a sample. Ring no longer draws a missing day as power on.
- Countdowns update exactly on the minute. Auto-refresh catches up after sleep, retries every 2 minutes while offline and refreshes as soon as the network is back.
- First launch opens settings on Places.
- Settings: removing a place can be undone, places can be reordered with the keyboard, arrow keys work in option groups and focus stays in place after each change.
- Settings: test notification reports when Windows blocks notifications; "Refresh now" shows progress; lead time is disabled while both warnings are off.
- Better contrast for "possible outage" text, danger buttons and focus outlines in the light theme.

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
