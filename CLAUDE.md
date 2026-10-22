# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Purpose

A Bitwig Studio controller script: it reads the modulated value of a Remote
Control on a pinned device and sends it as 14-bit MIDI Pitch Bend to a MIDI
output port. It exists in two forms:

- `PitchBendBridge.control.js` — the original JavaScript controller script; no
  build step and no dependencies.
- `src/main/scala/` — a Scala 3 port packaged as a Java extension
  (`.bwextension`), built with sbt. See "Scala port" below.

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

## Scala port

```fish
sbt test       # unit tests (munit)
sbt assembly   # target/PitchBendBridge.bwextension
```

For a manual check in Bitwig, copy `target/PitchBendBridge.bwextension` to
`~/Documents/Bitwig Studio/Extensions/`.

Behaviour must stay identical to the JavaScript script. The code is split into
a functional core and an imperative shell:

- `Bridge.scala` — pure: constants and setting defaults, `Message`, an
  immutable `State` and `step(state, event) => (state, commands)`. `Command`
  lists what the shell must do, in order: send MIDI, pin the cursors, follow
  the selection again, show a popup. `Message`
  has a private constructor; its `apply` clamps the channel and the value, and
  the MIDI bytes are derived from them. Transitions are composed from `Step`
  functions with `>>`, and `withState` picks the next step by the current
  state; `send` drops a value equal to `lastSent`, and `forget` clears it
  before a forced send.
- `PitchBendBridgeExtension.scala` — the only place with mutable state and
  Bitwig calls. It creates all observers in `init`, turns callbacks into
  `Bridge.Event`s and executes the returned commands in `execute`. The state is
  a local `var` in `init`; the only mutable field is `dispatch`.
- `PitchBendBridgeDefinition.scala` — metadata, registered through
  `src/main/resources/META-INF/services/com.bitwig.extension.ExtensionDefinition`.
  Its UUID intentionally differs from the JavaScript script so both can be
  installed at once; do not change it either.

Build notes:

- `com.bitwig:extension-api` comes from `https://maven.bitwig.com` and is
  `Provided`: Bitwig supplies it at runtime. `getRequiredAPIVersion` and the
  dependency version must match (18, as `loadAPI(18)` in the script).
- sbt-assembly bundles the Scala standard library into the jar; Bitwig does not
  provide it.
- `BridgeSuite` ports `test/mock-host.test.js` to `Bridge.step` and adds
  exhaustive checks of `Message` over all values and channels. Pinning and
  popups are checked as commands; the shell (`execute`, observer wiring) has
  no automated tests.
- `scalacOptions` enable `-Wunused:all`, `-Wvalue-discard`,
  `-Wnonunit-statement` and `-Wsafe-init`; the build must compile without
  warnings. `.scalafmt.conf` rewrites to Scala 3 syntax without optional
  braces; `sbt scalafmtAll` applies it. sbt-scalafmt needs sbt 1.12.9+.

## CI and releases

Versions follow SemVer (`0.1.0`, `0.2.0-rc.1`). The version lives in
`version.sbt` and in the `host.defineController` call of the script;
`PitchBendBridgeDefinition.getVersion` reads it from the generated
`BuildInfo`. Do not bump versions by hand: the release workflow does it.

- `.github/actions/verify` is shared by both workflows: it checks that
  `version.sbt` and the script share a version, runs
  `node test/mock-host.test.js`, then
  `sbt scalafmtCheckAll scalafmtSbtCheck test assembly`.
- `.github/workflows/ci.yml` runs it on pushes to `main` and on pull requests
  and uploads the `.bwextension` as an artifact.
- `.github/workflows/release.yml` is started by hand (Actions → Release → Run
  workflow, on `main`) with a version. It sets the version in `version.sbt`
  and the script, runs `verify`, pushes a "Release vX" commit and an annotated
  tag to `main` atomically, and publishes a GitHub release with
  `PitchBendBridge.control.js` and the `.bwextension`. A version with a
  suffix becomes a prerelease. It relies on `main` accepting pushes from
  `GITHUB_TOKEN`; branch protection that requires pull requests breaks it.
- The release workflow edits the script's version with `sed`: it expects the
  version argument of `host.defineController` alone on its line, indented by
  two spaces and followed by a comma. Keep that layout.

The editor setup targets VSCode with Metals (`.vscode/`, `.scalafmt.conf`);
Metals imports the sbt build through Bloop.
