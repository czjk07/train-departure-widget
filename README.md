# Train Departure Widget

An Android home-screen widget that helps you time your departure from the office.

It shows the **closest train you can still catch**, given a 15-minute lead time from
your desk, plus countdowns to the **Bus 1 (114)** and **Bus 2 (106)** departures that
connect to that train.

## How it works

- Trains run at `:04` and `:34` past the hour (16:04 – 18:34), Monday–Friday.
- Bus 1 (114) departs 13 minutes before each train; Bus 2 (106) departs 10 minutes
  before. The bus ride is ~4 minutes.
- Timing constants: `BUFFER_MIN = 15` (now → out of your seat), `WALK_MIN = 3`
  (out of seat → on the bus), `RIDE_MIN = 4` (bus ride to the train stop).
- A bus is catchable when it departs at least **18 minutes** (`BUFFER + WALK`)
  from now; the target train is the earliest train whose later bus is catchable —
  e.g. at 15:50 the 16:04 train is already lost, so the widget shows the **16:34**
  train with buses at 16:21 / 16:24.
- Each bus card shows a live `M:SS` countdown to departure (self-ticking
  Chronometer), colored by slack (light blue > 10 min, amber > 3 min, red
  otherwise), plus `departs HH:mm` and `out of seat by HH:mm` (departure − 3 min
  walk).
- The footer shows `Arrive at stop HH:mm · N min wait` (first catchable bus +
  4-minute ride) and a live clock.
- Weekends show “Weekend — no trains on this timetable”; after the last catchable
  train, “No more trains today”.
- Tap the widget to force a refresh; countdowns and the clock tick every second
  on their own, and the timetable logic refreshes once a minute.

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
  per-second updates
- `app/src/test/` — JUnit tests for the selection logic, including the
  15:50 → 16:34 example

Requires Android 8.0 (API 26) or newer.
