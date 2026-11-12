# Train Win Widget

A tiny Windows taskbar widget showing the next train home — the Windows sibling of
the [train-departure-widget](https://github.com/czjk07/train-departure-widget)
Android app.

## What it does

- Docks a see-through text overlay **inside the taskbar**, just left of the system tray
- Shows a compact line: `16:34 · 114 in 21m · 106 in 24m`
- The current train stays visible until its Bus 106 departs — no rolling to the next train early
- Turns **orange** when a bus is 3–10 minutes away, **red** under 3 minutes
- **Hover** for a tooltip with the details: bus departure times, "leave desk" times, arrive-at-stop and wait
- Adapts to light and dark taskbars
- Updates once a minute
- Tiny: one static exe (~450 KB), no installer, no runtime

## Timetable

Trains 16:04–18:34 at `:04`/`:34`, Monday–Friday. Bus 114 departs 13 minutes
before each train, Bus 106 departs 10 minutes before. The timetable is hardcoded
in `src/schedule.rs` — edit the `TRAINS` table if it changes.

## Run

1. Download `train-win-widget.exe` from the latest **Build Windows exe** run in the
   [Actions tab](../../actions) (artifact `train-win-widget-exe`), or build from
   source with `cargo build --release`
2. Double-click the exe — no setup
3. Right-click the widget → **Exit** to close it

SmartScreen may warn on first run because the exe isn't signed —
**More info → Run anyway**.

## Build from source

```
cargo build --release
```
