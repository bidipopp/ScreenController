# iiyama ScreenController (Android)

Android app (Kotlin + Jetpack Compose) to control iiyama **TFxx39AS** displays using **RS232 over LAN (TCP port 5000)**.

Main goal: quickly apply the same settings to multiple displays on a local network (power, color settings, remote lock), with a status result for each command.

## What the project currently does

- Test UI to send commands to one display (IP/host, port, monitor ID, timeout).
- Protocol frame building with XOR checksum.
- Async TCP sending and response parsing.
- Handles normal states: `ACK`, `NACK`, `NAV`, timeout, unknown host, connection refused/closed.
- Detailed diagnostics report (TX/RX hex, CONNECT/WRITE/READ/PARSE steps, durations).

## Requirements

- Recent Android Studio (with Android SDK)
- JDK 17
- An iiyama TFxx39AS-compatible display on the same local network

## Run the app

From the project root:

```bash
.\gradlew.bat :app:assembleDebug
```

Then run the app on an emulator/device from Android Studio (main activity: `MainActivity`).

## APK artifact (always available)

A debug APK is built and published automatically as a GitHub Actions artifact:

- Workflow: `.github/workflows/android-apk.yml`
- Artifact name: `ScreenController-debug-apk`

How to download it:

1. Open the repository **Actions** tab.
2. Open the latest successful **Build Android APK** run.
3. Download the `ScreenController-debug-apk` artifact.

You can also trigger a fresh build at any time with **Run workflow** (`workflow_dispatch`).

## Quick usage

1. Open the app.
2. Fill in:
   - Display **IP or host**
   - **Port** (default `5000`)
   - **Monitor ID** (`0..255`, default `1`)
   - Network **timeout** in ms
3. Tap an action (Power, Remote lock/unlock, Color).
4. Read the result and diagnostics block.

## Available remote commands

> Source: `docs/RS232-LAN_commands_2025_03_TFxx39AS...` and implementation in `app/src/main/java/.../protocol/Commands.kt`.

### Commands exposed in the current UI

- **Power ON** (`Set Power`, code `0x18`, data `0x02`)
- **Power OFF** (`Set Power`, code `0x18`, data `0x01`)
- **Remote Lock** (`Set IR Remote`, code `0x1C`, data `0x02`)
- **Remote Unlock** (`Set IR Remote`, code `0x1C`, data `0x01`)
- **Color 6500K** (`Set Color Temperature`, code `0x34`, data `0x06`)
- **Color 7500K** (`Set Color Temperature`, code `0x34`, data `0x05`)
- **Color 9300K** (`Set Color Temperature`, code `0x34`, data `0x04`)

### Commands implemented in the protocol layer

- **Reset**
  - `ResetCommand`: code `0x01`
  - Known vector: `A6 01 00 00 00 03 01 01 A4`
- **Power state**
  - `Set`: `0x18`
  - `Get`: `0x19`
- **IR Remote Lock**
  - `Set`: `0x1C`
  - `Get`: `0x1D`
- **Color Temperature**
  - `Set`: `0x34`
  - `Get`: `0x35`
- **Color Parameters (RGB gain/offset)**
  - `Set`: `0x36`
  - `Get`: `0x37`
- **Video Parameters**
  - `Set`: `0x32`
  - `Get`: `0x33`

## Useful development commands

```bash
# Build debug
.\gradlew.bat :app:assembleDebug

# JVM unit tests
.\gradlew.bat :app:testDebugUnitTest

# Run all tests
.\gradlew.bat test
```

## Project structure

```text
app/src/main/java/com/immensive/screencontroller/
  protocol/   frames, checksum, commands, TCP client
  data/       command sending repository
  ui/         Compose test screen + ViewModel

app/src/test/java/com/immensive/screencontroller/
  protocol/   protocol tests + fake TCP server
  ui/         ViewModel tests

docs/         RS232/LAN notes and display manual
```

## Notes

- Network transport is **raw TCP** (not HTTP).
- Required Android permission: `INTERNET`.
- Network errors are handled as normal domain states, without app crashes.
