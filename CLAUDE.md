# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Purpose

A Bitwig Studio controller script: it reads the modulated value of a Remote
Control on a pinned device and sends it as 14-bit MIDI Pitch Bend to a MIDI
output port. All code lives in one file, `PitchBendBridge.control.js`; there is
no build step and no dependencies.

The whole repository (code, comments, docs, commit messages) is in English.

## Commands

```fish
node test/mock-host.test.js
```

There is no test framework: each test is a `{ ... }` block with `assert`, and
the first failure stops the run. To run a single test, temporarily comment out
the other blocks.

For a manual check in Bitwig, copy the script to
`~/Documents/Bitwig Studio/Controller Scripts/`; Bitwig picks it up from there.

## Language constraints

- `PitchBendBridge.control.js` must be strict ES5 (`var`, `function`; no arrow
  functions, `const`/`let` or template strings): Bitwig's engine does not
  guarantee ES6.
- The test runs in Node and may use modern syntax.

## Script structure

The file has three layers:

1. Pure functions (`clamp`, `snapToCenter`, `pitchBendMessage`, `range`) with
   no access to `host` or state.
2. The single mutable `state` object and effects (`send`, `emit`, `resetBend`,
   `reEmitCurrent`). `send` drops a value equal to `state.lastSent`; functions
   that need a forced send reset `lastSent` to `-1` first.
3. The global `init`/`flush`/`exit` called by Bitwig. All observers, cursors
   and settings are created inside `init` only — the Bitwig API does not allow
   creating them later.

The values of all 8 Remote Controls are cached in `state.latest`, so switching
the source or channel re-sends the current value without waiting for a new
event from Bitwig.

Settings come in two kinds: `host.getPreferences()` is global (channel,
deadband), `host.getDocumentState()` is saved with the project (Remote Control
number, Pin/Unpin/Reset).

Do not change the UUID in `host.defineController`: Bitwig uses it to identify
an already added controller.

## Tests

`test/mock-host.test.js` runs the script in a `vm` context against a minimal
mock of the Bitwig API:

- Any new call to `host`, cursors or settings in the script must be added to the
  mock in `loadScript`, otherwise the test fails with a `TypeError`.
- The `getNumberSetting` mock calls the observer immediately with the initial
  value, as Bitwig does; `init` therefore already produces sends, and
  `loadScript` clears `sent` after it.
- `sent` collects both MIDI triples `[status, lsb, msb]` and marker entries
  `["pinTrack", true]`, `["popup", text]`.
- Observers are looked up by setting label (`numbers["MIDI channel"]`,
  `signals["Pin"]`). Renaming a setting in the script requires updating the
  tests and the settings table in `README.md`.
