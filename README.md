# Train Departure Widget

An Android home-screen widget that helps you time your departure from the office.

It shows the **closest train you can still catch**, given a 15-minute lead time from
your desk, plus countdowns to the **Bus 1 (114)** and **Bus 2 (106)** departures that
connect to that train.

## How it works

- Trains run at `:04` and `:34` past the hour (16:04 – 18:34).
- Bus 1 (114) departs 13 minutes before each train; Bus 2 (106) departs 10 minutes
  before. The bus ride is ~4 minutes.
- The target train is the **earliest train whose latest usable bus departs at least
  15 minutes from now** — e.g. at 15:50 the 16:04 train is already lost (its buses
  leave at 15:51/15:54), so the widget shows the **16:34** train with buses at
  16:21 / 16:24.
- Once no train is catchable within the 15-minute rule, the widget shows dashes.
- Tap the widget to force a refresh; it also refreshes automatically every minute.

## Schedule

| Train | Bus 1 (114) | Bus 2 (106) |
|-------|-------------|-------------|
| 16:04 | 15:51       | 15:54       |
| 16:34 | 16:21       | 16:24       |
| 17:04 | 16:51       | 16:54       |
| 17:34 | 17:21       | 17:24       |
| 18:04 | 17:51       | 17:54       |
| 18:34 | 18:22       | 18:25       |

The schedule is hardcoded in `app/src/main/java/com/czjk/trainwidget/Schedule.kt`
and applies every day. Edit the `trains` list there if the timetable changes; a
live GTFS data source can be added later behind the same interface.

## Build & install

1. Open the project in Android Studio (Hedgehog or newer).
2. If prompted that the Gradle wrapper JAR is missing, accept the IDE's offer to
   repair it, or run `gradle wrapper` locally. (The wrapper scripts and
   `gradle-wrapper.properties` are included; the binary JAR is not committed.)
3. Run **Build > Build APK(s)** or deploy to a device with **Run**.
4. Long-press your home screen > **Widgets** > **Train Departure** and drop it on
   the home screen.

## Project layout

- `Schedule.kt` — timetable + target-train selection logic (pure Kotlin, unit-tested)
- `DepartureWidget.kt` — `AppWidgetProvider` that renders state and schedules the
  minute-tick updates
- `app/src/test/` — JUnit tests for the selection logic, including the
  15:50 → 16:34 example

Requires Android 8.0 (API 26) or newer.
