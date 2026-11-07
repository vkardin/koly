# Коли Є — Windows widget

Desktop widget for Windows 10/11 built with Tauri 2 and vanilla TypeScript.

## Requirements

- Node.js LTS
- Rust (stable, `x86_64-pc-windows-msvc`) and Visual Studio Build Tools with the C++ workload
- WebView2 runtime (preinstalled on Windows 11; the installer downloads it if missing)

## Commands

```
npm install
npm run tauri dev      # run in development mode
npm test               # schedule and data tests
npm run bundle         # release build -> release/koly-widget-<version>-setup.exe
```

Release builds sign the auto-update package. Set `TAURI_SIGNING_PRIVATE_KEY`
(the key file path or its contents) before `npm run bundle`. The key is never
stored in the repository; CI reads it from the `TAURI_SIGNING_PRIVATE_KEY` secret.

## Releases

Push a tag `desktop-vX.Y.Z` that matches the version in `src-tauri/tauri.conf.json`.
The `desktop-release` workflow builds the installer, publishes
`koly-widget-X.Y.Z-setup.exe` and refreshes the `desktop-latest` feed that the
widget checks for updates. Release notes come from the `## desktop X.Y.Z`
section of the root `CHANGELOG.md`.

## Data

The widget reads the normalized schedule JSON published at
`https://vkardin.github.io/koly/v1/` (`index.json`, `regions/<id>.json`).
Responses are cached on disk in the app data folder, so the last schedule is
available offline. Until the data service is live the widget shows clearly
labelled sample schedules instead of real data.

## Structure

- `src/core` — data loading, schedule logic (Europe/Kyiv time), i18n (uk/en/ru), notifications
- `src/widget.ts` — the widget window (Strip / Card / Ring views)
- `src/settings.ts` — the settings window
- `src-tauri` — native shell: tray menu, settings window, file storage, installer config
