# Pitch Bend Bridge

A [Bitwig Studio](https://www.bitwig.com/) controller script that turns a **Remote Control** of any device into **14-bit MIDI Pitch Bend** on a MIDI output port.

Bitwig has no device or Grid module that sends channel Pitch Bend. This script fills the gap: whatever moves the Remote Control — automation, a modulator, a Grid patch — is sent to your hardware synth as Pitch Bend.

## Features

- Full 14-bit Pitch Bend resolution (0–16383).
- Reads the **modulated** value of the Remote Control, so automation and modulation both drive the bend.
- Pin the source device so you can keep working elsewhere in the project.
- Selectable MIDI channel and source Remote Control (1–8).
- Center deadband, so the middle position is an exact `8192` (no detune).
- Sends center Pitch Bend when the channel changes, the source device disappears, or the script exits.
- Sends only on change.

## Requirements

- Bitwig Studio with Controller API version 18 or newer.
- A MIDI output port connected to the instrument you want to bend.

## Installation

1. Download `PitchBendBridge.control.js` from the [latest release](https://github.com/argent-smith/bitwig-pitch-bend-bridge/releases/latest) and copy it into the `Controller Scripts` folder of your Bitwig Studio user directory, for example `~/Documents/Bitwig Studio/Controller Scripts/` on macOS or `Documents\Bitwig Studio\Controller Scripts\` on Windows.
2. In Bitwig: **Settings → Controllers → Add Controller → Mostly Random → Pitch Bend Bridge**.
3. Choose the **MIDI output** port of your instrument.

## Setup

1. Pick or create a **source device** with Remote Controls. A Note Grid works well as a holder: map its Remote Control 1 to any parameter inside the patch (for example a `Value` module).
2. Select that device, open the controller's settings (the **Pitch Bend Bridge** icon in the window header), and press **Pin selected device**. A popup confirms the pinned track and device.
3. Set **MIDI channel** to the receive channel of your instrument.

### Settings

| Setting | Where | Default | Description |
|---|---|---|---|
| MIDI channel | Preferences → Output | 1 | Channel the Pitch Bend is sent on |
| Center deadband | Preferences → Output | 2 | Values this many steps from 8192 are snapped to center |
| Remote Control # | Project → Source | 1 | Which Remote Control (1–8) drives the bend |
| Pin selected device | Project → Source | – | Lock onto the currently selected device |
| Follow selection | Project → Source | – | Unpin; follow the selected device again |
| Center pitch bend | Project → Output | – | Send center (8192) now |

Project settings are saved with the Bitwig project.

## Usage

The Remote Control's middle position is "no bend"; the extremes are full bend down and up. The bend range in semitones is set on the receiving instrument.

- **Automation:** draw automation for the Remote Control on the pinned device.
- **Modulator:** add an LFO, Steps or any other modulator to the pinned device and route it to the Remote Control. Leave the knob in the middle so the modulation swings around center.
- **Grid:** route a `Modulator Out` module from the Grid patch to the same Grid's Remote Control.

## Limitations

- **MIDI port sharing.** The output port is owned by the controller. Depending on your setup, the same port may not be usable by a Hardware Instrument at the same time. Route notes and Pitch Bend through a virtual MIDI bus or a MIDI interface if needed.
- **Timing.** MIDI from controller scripts is not sample-accurate or latency-compensated. This is fine for LFO-style bends, but may be noticeable for bends that must hit an exact beat.
- **Modulation visibility.** The script reads `modulatedValue()` of the Remote Control. If modulation applied to the mapped target parameter is not reflected there, modulate the Remote Control itself.
- **Pinning across reloads.** Whether the pin survives reopening a project is not guaranteed. Press **Pin selected device** again if the bend stops responding.

## Development

The script is plain ES5 JavaScript, since Bitwig's script engine does not guarantee newer language features.

A small test suite runs the script against a mock of the controller API with Node.js:

```sh
node test/mock-host.test.js
```

### Scala extension

The same controller is also available as a Scala 3 port, packaged as a Bitwig
Java extension. It appears as **Mostly Random → Pitch Bend Bridge (Scala)** and
can be installed next to the script. Building requires a JDK 17+ and
[sbt](https://www.scala-sbt.org/):

```sh
sbt test        # run the unit tests
sbt scalafmtAll # format the code
sbt assembly    # build target/PitchBendBridge.bwextension
```

Copy `target/PitchBendBridge.bwextension` into the `Extensions` folder of your
Bitwig Studio user directory, for example
`~/Documents/Bitwig Studio/Extensions/` on macOS. Each
[release](https://github.com/argent-smith/bitwig-pitch-bend-bridge/releases)
also has a ready-built `.bwextension` attached.

### Continuous integration

GitHub Actions runs both test suites, checks formatting and builds the
extension on every pull request and push to `main`.

To release, run **Actions → Release → Run workflow** on `main` and enter a
[SemVer](https://semver.org/) version such as `0.2.0` (or `0.2.0-rc.1` for a
prerelease). The workflow sets the version in the script and the build, tests
and builds both forms, tags the release commit and publishes a GitHub release
with `PitchBendBridge.control.js` and `PitchBendBridge.bwextension`.

For VSCode, install the recommended Metals extension and open the repository
folder; Metals imports the sbt build automatically.

## License

[MIT](LICENSE) © 2026 Pavel Argentov
