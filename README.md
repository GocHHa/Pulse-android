# Pulse — phone hardware monitor

Kotlin · Jetpack Compose (Material 3) · MVVM with manual DI · Room · WorkManager · DataStore · Glance widget.

## Toolchain
| Piece | Version |
|---|---|
| Android Gradle Plugin | 9.3.2 (built-in Kotlin, so no `kotlin-android` plugin) |
| Gradle | 9.5.0 (wrapper included) |
| Kotlin / Compose compiler plugin | 2.4.20 |
| KSP (for Room) | 2.3.12 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 29 |

## Open and run
1. Android Studio › **File › Open** › select this `Pulse` folder (the one containing `settings.gradle.kts`).
2. Let Gradle sync. If prompted, install **Android SDK Platform 37** and accept licenses.
3. If sync says the Gradle JDK is unsupported: **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** → choose the bundled JetBrains Runtime 21 (or any JDK 17–21).
4. Plug in the phone (USB debugging on), pick it in the device menu, press **Run**.

## Project map
```
app/src/main/java/dev/pulse/monitor/
  PulseApp.kt            Application + AppContainer (manual DI)
  MainActivity.kt        Edge-to-edge host, theme, first-launch permission
  data/battery/          BatteryReader, CurrentNormalizer (µA/mA + sign), DrainTracker
  data/system/           CpuReader (sysfs + thermal), MemoryReader (RAM + StatFs)
  data/network/          NetworkReader (TrafficStats, Wi-Fi, mobile signal)
  data/history/          Room entity/DAO/database/repository
  data/settings/         DataStore settings
  alerts/                AlertRules (pure logic) + AlertEngine + notifications
  work/HistoryWorker.kt  15-minute logging, pruning, alerts, widget refresh
  service/               Live monitoring foreground service (specialUse)
  widget/                Glance home-screen widget
  ui/                    Theme, components (gauges, charts, equalizer), screens, ViewModels
```

Fonts: Saira and Inter, SIL Open Font License (see `licenses/`).
