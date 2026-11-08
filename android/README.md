# Koly Ye for Android

Kotlin, Jetpack Compose and Material 3. Reads the published schedules from
`https://vkardin.github.io/koly/v1/` and never contacts power companies directly.

## Modules

| Module | Purpose |
|---|---|
| `app` | Activity, navigation, onboarding, sync, notifications |
| `core:model` | Schema v1 and schedule logic (plain Kotlin, unit tested) |
| `core:data` | Network with ETag, Room cache, DataStore settings |
| `core:ui` | Theme, fonts, strings (uk, en, ru), day bar and ring |
| `feature:home` | Home screen in three views |
| `feature:places` | Up to 10 saved places |
| `feature:stats` | Hours without power over 7 days, by schedule |
| `feature:settings` | Appearance, notifications, language, about |
| `widget` | Glance widgets: Cards (2×2, 4×2, 4×3) and Minimal (ring, pill, text) |

## Build

JDK 21 (the one bundled with Android Studio works):

```
./gradlew :core:model:test :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/koly-<version>-debug.apk`.
