// Pitch Bend Bridge — a Bitwig Studio controller script
//
// Reads Remote Control N of a pinned device — its value including
// automation and modulation — and sends it as 14-bit MIDI Pitch Bend
// to the selected MIDI output (e.g. a hardware synth).
//
// Install: copy this file to ~/Documents/Bitwig Studio/Controller Scripts/
// then Settings → Controllers → Add Controller → "Mostly Random" →
// "Pitch Bend Bridge" and choose the MIDI output port.
//
// Written in ES5: Bitwig's JavaScript engine does not guarantee ES6 support.
//
// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Pavel Argentov

loadAPI(18);

host.defineController(
  "Mostly Random",
  "Pitch Bend Bridge",
  "0.1.0",
  "ff438bf3-4285-4c49-8754-ada9b8ec42e9",
  "Pavel Argentov"
);
host.defineMidiPorts(0, 1);

// ---------------------------------------------------------------------------
// Pure functions
// ---------------------------------------------------------------------------

var BEND_RANGE = 16384;   // 14 bits: 0..16383
var BEND_CENTER = 8192;
var REMOTE_COUNT = 8;

function clamp(lo, hi, x) {
  return Math.max(lo, Math.min(hi, x));
}

// Snap values within `deadband` steps of the center to the exact center.
function snapToCenter(deadband, v) {
  return Math.abs(v - BEND_CENTER) <= deadband ? BEND_CENTER : v;
}

// A Pitch Bend MIDI message as a plain immutable record.
function pitchBendMessage(channel1to16, value14) {
  var v = clamp(0, BEND_RANGE - 1, Math.round(value14));
  return {
    status: 0xE0 | (clamp(1, 16, channel1to16) - 1),
    lsb: v & 0x7F,
    msb: (v >> 7) & 0x7F,
    value: v
  };
}

function range(n) {
  var xs = [];
  for (var i = 0; i < n; i++) xs.push(i);
  return xs;
}

// ---------------------------------------------------------------------------
// State (the only mutable place) and effects
// ---------------------------------------------------------------------------

var state = {
  channel: 1,
  remoteIndex: 0,     // 0-based
  deadband: 2,
  lastSent: -1,
  latest: []          // latest value of each Remote Control
};

var midiOut;
var cursorTrack;
var cursorDevice;

function send(msg) {
  if (msg.value === state.lastSent) return;
  midiOut.sendMidi(msg.status, msg.lsb, msg.msb);
  state.lastSent = msg.value;
}

function emit(rawValue14) {
  send(pitchBendMessage(state.channel, snapToCenter(state.deadband, rawValue14)));
}

function resetBend() {
  state.lastSent = -1;
  send(pitchBendMessage(state.channel, BEND_CENTER));
}

function onRemoteChanged(index, value14) {
  state.latest[index] = value14;
  if (index === state.remoteIndex) emit(value14);
}

function reEmitCurrent() {
  state.lastSent = -1;
  var v = state.latest[state.remoteIndex];
  if (v !== undefined) emit(v);
}

// ---------------------------------------------------------------------------
// init / flush / exit
// ---------------------------------------------------------------------------

function init() {
  midiOut = host.getMidiOutPort(0);

  // The cursors follow the selection until they are pinned with "Pin".
  cursorTrack = host.createCursorTrack("pbb-track", "Pitch Bend Bridge", 0, 0, true);
  cursorDevice = cursorTrack.createCursorDevice(
    "pbb-device", "Pitch Bend Bridge", 0,
    CursorDeviceFollowMode.FOLLOW_SELECTION
  );
  var remotes = cursorDevice.createCursorRemoteControlsPage(REMOTE_COUNT);

  // Value including automation and modulation, 14-bit resolution.
  range(REMOTE_COUNT).forEach(function (i) {
    remotes.getParameter(i).modulatedValue().addValueObserver(BEND_RANGE, function (v) {
      onRemoteChanged(i, v);
    });
  });

  // Device gone → bend back to center.
  cursorDevice.exists().addValueObserver(function (exists) {
    if (!exists) resetBend();
  });

  cursorDevice.name().markInterested();
  cursorTrack.name().markInterested();
  cursorDevice.isPinned().markInterested();

  // --- Global preferences (Settings → Controllers) ---
  var prefs = host.getPreferences();

  prefs.getNumberSetting("MIDI channel", "Output", 1, 16, 1, "", 1)
    .addRawValueObserver(function (v) {
      resetBend();                       // release the bend on the old channel
      state.channel = Math.round(v);
      reEmitCurrent();
    });

  prefs.getNumberSetting("Center deadband", "Output", 0, 64, 1, "steps", 2)
    .addRawValueObserver(function (v) {
      state.deadband = Math.round(v);
    });

  // --- Per-project settings (saved with the project) ---
  var doc = host.getDocumentState();

  doc.getNumberSetting("Remote Control #", "Source", 1, REMOTE_COUNT, 1, "", 1)
    .addRawValueObserver(function (v) {
      state.remoteIndex = clamp(0, REMOTE_COUNT - 1, Math.round(v) - 1);
      reEmitCurrent();
    });

  doc.getSignalSetting("Pin", "Source", "Pin selected device")
    .addSignalObserver(function () {
      cursorTrack.isPinned().set(true);
      cursorDevice.isPinned().set(true);
      host.showPopupNotification(
        "PB Bridge → " + cursorTrack.name().get() + " / " + cursorDevice.name().get()
      );
      reEmitCurrent();
    });

  doc.getSignalSetting("Unpin", "Source", "Follow selection")
    .addSignalObserver(function () {
      cursorDevice.isPinned().set(false);
      cursorTrack.isPinned().set(false);
      resetBend();
      host.showPopupNotification("PB Bridge: follows selection");
    });

  doc.getSignalSetting("Reset", "Output", "Center pitch bend")
    .addSignalObserver(resetBend);

  host.println("Pitch Bend Bridge ready");
}

function flush() {}

function exit() {
  resetBend();
}
