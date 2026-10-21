// Runs PitchBendBridge.control.js against a minimal mock of the Bitwig
// controller API and checks the MIDI it sends.
//
// Usage: node test/mock-host.test.js

"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");

function loadScript() {
  const sent = [];
  const remoteObservers = {};
  const signals = {};
  const numbers = {};
  let existsObserver = null;

  const stringValue = (s) => ({ markInterested() {}, get: () => s });
  const pinValue = (tag) => ({ markInterested() {}, set: (v) => sent.push([tag, v]) });

  const remotes = {
    getParameter: (i) => ({
      modulatedValue: () => ({
        addValueObserver: (_range, cb) => { remoteObservers[i] = cb; }
      })
    })
  };

  const device = {
    createCursorRemoteControlsPage: () => remotes,
    exists: () => ({ addValueObserver: (cb) => { existsObserver = cb; } }),
    name: () => stringValue("Device"),
    isPinned: () => pinValue("pinDevice")
  };

  const track = {
    createCursorDevice: () => device,
    name: () => stringValue("Track"),
    isPinned: () => pinValue("pinTrack")
  };

  const settings = () => ({
    getNumberSetting: (label, _c, _min, _max, _step, _unit, initial) => ({
      addRawValueObserver: (cb) => { numbers[label] = cb; cb(initial); }
    }),
    getSignalSetting: (label) => ({
      addSignalObserver: (cb) => { signals[label] = cb; }
    })
  });

  const context = {
    loadAPI() {},
    CursorDeviceFollowMode: { FOLLOW_SELECTION: 0 },
    host: {
      defineController() {},
      defineMidiPorts() {},
      getMidiOutPort: () => ({ sendMidi: (s, d1, d2) => sent.push([s, d1, d2]) }),
      createCursorTrack: () => track,
      getPreferences: settings,
      getDocumentState: settings,
      showPopupNotification: (text) => sent.push(["popup", text]),
      println() {}
    }
  };

  vm.createContext(context);
  const file = path.join(__dirname, "..", "PitchBendBridge.control.js");
  vm.runInContext(fs.readFileSync(file, "utf8"), context);
  context.init();
  sent.length = 0;

  return { context, sent, remoteObservers, signals, numbers, exists: () => existsObserver };
}

// --- Tests ---------------------------------------------------------------

{
  const t = loadScript();
  t.remoteObservers[0](16383);
  assert.deepStrictEqual(t.sent, [[0xE0, 127, 127]], "max value → 0x3FFF on channel 1");
}

{
  const t = loadScript();
  t.remoteObservers[0](16383);
  t.remoteObservers[0](16383);
  assert.strictEqual(t.sent.length, 1, "duplicate values are not re-sent");
}

{
  const t = loadScript();
  t.remoteObservers[1](0);
  assert.deepStrictEqual(t.sent, [], "only the selected Remote Control is sent");
}

{
  const t = loadScript();
  t.remoteObservers[0](0);
  t.remoteObservers[0](8191);
  assert.deepStrictEqual(t.sent[1], [0xE0, 0, 64], "near-center value snaps to 8192");
}

{
  const t = loadScript();
  t.remoteObservers[0](0);
  t.numbers["MIDI channel"](3);
  assert.deepStrictEqual(
    t.sent,
    [[0xE0, 0, 0], [0xE0, 0, 64], [0xE2, 0, 0]],
    "channel change centers the old channel and re-sends on the new one"
  );
}

{
  const t = loadScript();
  t.remoteObservers[0](0);
  t.remoteObservers[1](16383);
  t.sent.length = 0;
  t.numbers["Remote Control #"](2);
  assert.deepStrictEqual(t.sent, [[0xE0, 127, 127]], "switching source re-sends its value");
}

{
  const t = loadScript();
  t.remoteObservers[0](0);
  t.exists()(false);
  assert.deepStrictEqual(t.sent[1], [0xE0, 0, 64], "removed device centers the bend");
}

{
  const t = loadScript();
  t.signals["Pin"]();
  assert.deepStrictEqual(t.sent.slice(0, 2), [["pinTrack", true], ["pinDevice", true]], "Pin pins track and device");
}

{
  const t = loadScript();
  t.remoteObservers[0](0);
  t.context.exit();
  assert.deepStrictEqual(t.sent[1], [0xE0, 0, 64], "exit centers the bend");
}

console.log("All tests passed.");
