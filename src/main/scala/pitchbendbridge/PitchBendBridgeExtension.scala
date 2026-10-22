// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Pavel Argentov

package pitchbendbridge

import com.bitwig.extension.callback.BooleanValueChangedCallback
import com.bitwig.extension.controller.ControllerExtension
import com.bitwig.extension.controller.api.{ControllerHost, CursorDeviceFollowMode}

import Bridge.*
import Bridge.Event.*
import Bridge.Command.*

// The imperative shell: wires Bitwig observers to Bridge.step and executes
// the resulting commands. All observers, cursors and settings are created
// inside `init`; the Bitwig API does not allow creating them later.
final class PitchBendBridgeExtension(definition: PitchBendBridgeDefinition, host: ControllerHost)
    extends ControllerExtension(definition, host):

  // Set in `init`: the MIDI output port and the cursors exist only from then on.
  private var dispatch: Event => Unit = _ => ()

  override def init(): Unit =
    val midiOut = getMidiOutPort(0)

    // The cursors follow the selection until they are pinned with "Pin".
    val cursorTrack = host.createCursorTrack("pbb-track", "Pitch Bend Bridge", 0, 0, true)
    val cursorDevice = cursorTrack.createCursorDevice(
      "pbb-device",
      "Pitch Bend Bridge",
      0,
      CursorDeviceFollowMode.FOLLOW_SELECTION
    )
    val remotes = cursorDevice.createCursorRemoteControlsPage(RemoteCount)

    // Pins the track before the device and releases them in reverse order.
    def execute(command: Command): Unit = command match
      case Send(m) =>
        midiOut.sendMidi(m.status, m.lsb, m.msb)
      case PinCursors =>
        cursorTrack.isPinned.set(true)
        cursorDevice.isPinned.set(true)
      case FollowSelection =>
        cursorDevice.isPinned.set(false)
        cursorTrack.isPinned.set(false)
      case Popup(text) =>
        host.showPopupNotification(text)

    var state = State()

    dispatch = event =>
      val (next, commands) = Bridge.step(state, event)
      state = next
      commands.foreach(execute)

    // Value including automation and modulation, 14-bit resolution.
    (0 until RemoteCount).foreach: i =>
      remotes.getParameter(i).modulatedValue.addValueObserver(BendRange, v => dispatch(RemoteChanged(i, v)))

    // Device gone → bend back to center. Typed explicitly: `addValueObserver`
    // is overloaded, so a bare lambda is ambiguous.
    val onExists: BooleanValueChangedCallback = exists => dispatch(DeviceExists(exists))
    cursorDevice.exists.addValueObserver(onExists)

    cursorDevice.name.markInterested()
    cursorTrack.name.markInterested()
    cursorDevice.isPinned.markInterested()

    // --- Global preferences (Settings → Controllers) ---
    val prefs = host.getPreferences

    prefs
      .getNumberSetting("MIDI channel", "Output", 1, ChannelCount, 1, "", DefaultChannel)
      .addRawValueObserver(v => dispatch(ChannelChanged(rounded(v))))

    prefs
      .getNumberSetting("Center deadband", "Output", 0, MaxDeadband, 1, "steps", DefaultDeadband)
      .addRawValueObserver(v => dispatch(DeadbandChanged(rounded(v))))

    // --- Per-project settings (saved with the project) ---
    val doc = host.getDocumentState

    doc
      .getNumberSetting("Remote Control #", "Source", 1, RemoteCount, 1, "", 1)
      .addRawValueObserver(v => dispatch(SourceChanged(rounded(v))))

    doc
      .getSignalSetting("Pin", "Source", "Pin selected device")
      .addSignalObserver(() => dispatch(Pinned(cursorTrack.name.get, cursorDevice.name.get)))

    doc
      .getSignalSetting("Unpin", "Source", "Follow selection")
      .addSignalObserver(() => dispatch(Unpinned))

    doc
      .getSignalSetting("Reset", "Output", "Center pitch bend")
      .addSignalObserver(() => dispatch(ResetPressed))

    host.println("Pitch Bend Bridge (Scala) ready")

  override def flush(): Unit = ()

  override def exit(): Unit = dispatch(Exited)

  // Number settings report doubles; the core works with whole numbers.
  private def rounded(v: Double): Int = math.round(v).toInt
