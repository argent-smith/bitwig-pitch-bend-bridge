// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Pavel Argentov

package pitchbendbridge

import Bridge.*
import Bridge.Event.*

// Ports test/mock-host.test.js to the functional core: instead of a mocked
// Bitwig host, events are fed to Bridge.step and the MIDI triples are checked.
class BridgeSuite extends munit.FunSuite:

  // Bitwig calls the setting observers with their initial values during init;
  // the messages they produce are dropped, as `loadScript` does in the JS test.
  private val booted: State =
    run(State(), Seq(ChannelChanged(DefaultChannel), DeadbandChanged(DefaultDeadband), SourceChanged(1)))._1

  private def run(state: State, events: Seq[Event]): (State, Vector[(Int, Int, Int)]) =
    events.foldLeft((state, Vector.empty[(Int, Int, Int)])):
      case ((s, sent), event) =>
        val (next, messages) = Bridge.step(s, event)
        (next, sent ++ messages.map(m => (m.status, m.lsb, m.msb)))

  private def sent(events: Event*): Vector[(Int, Int, Int)] = run(booted, events)._2

  private val bendDown = (0xe0, 0, 0)
  private val center = (0xe0, 0, 64)

  test("max value → 0x3FFF on channel 1"):
    assertEquals(sent(RemoteChanged(0, 16383)), Vector((0xe0, 127, 127)))

  test("duplicate values are not re-sent"):
    assertEquals(sent(RemoteChanged(0, 16383), RemoteChanged(0, 16383)), Vector((0xe0, 127, 127)))

  test("only the selected Remote Control is sent"):
    assertEquals(sent(RemoteChanged(1, 0)), Vector.empty)

  test("near-center value snaps to 8192"):
    assertEquals(sent(RemoteChanged(0, 0), RemoteChanged(0, 8191)), Vector(bendDown, center))

  test("zero deadband does not snap"):
    assertEquals(sent(DeadbandChanged(0), RemoteChanged(0, 8191)), Vector((0xe0, 127, 63)))

  test("channel change centers the old channel and re-sends on the new one"):
    assertEquals(sent(RemoteChanged(0, 0), ChannelChanged(3)), Vector(bendDown, center, (0xe2, 0, 0)))

  test("switching source re-sends its value"):
    assertEquals(
      sent(RemoteChanged(0, 0), RemoteChanged(1, 16383), SourceChanged(2)),
      Vector(bendDown, (0xe0, 127, 127))
    )

  test("source number is clamped to the last Remote Control"):
    assertEquals(sent(RemoteChanged(7, 16383), SourceChanged(99)), Vector((0xe0, 127, 127)))

  test("removed device centers the bend"):
    assertEquals(sent(RemoteChanged(0, 0), DeviceExists(false)), Vector(bendDown, center))

  test("existing device sends nothing"):
    assertEquals(sent(RemoteChanged(0, 0), DeviceExists(true)), Vector(bendDown))

  test("Pin re-sends the current value even if it was already sent"):
    assertEquals(sent(RemoteChanged(0, 0), Pinned), Vector(bendDown, bendDown))

  test("Pin before any value sends nothing"):
    assertEquals(sent(Pinned), Vector.empty)

  test("Unpin centers the bend"):
    assertEquals(sent(RemoteChanged(0, 0), Unpinned), Vector(bendDown, center))

  test("Reset centers the bend"):
    assertEquals(sent(RemoteChanged(0, 0), ResetPressed), Vector(bendDown, center))

  test("exit centers the bend"):
    assertEquals(sent(RemoteChanged(0, 0), Exited), Vector(bendDown, center))

  test("Message clamps the channel and the value"):
    val high = Message(17, 99999)
    val low = Message(0, -5)
    assertEquals((high.status, high.lsb, high.msb, high.value), (0xef, 127, 127, 16383))
    assertEquals((low.status, low.lsb, low.msb, low.value), (0xe0, 0, 0, 0))

  test("every 14-bit value round-trips through the MIDI bytes"):
    (0 until BendRange).foreach: v =>
      val m = Message(1, v)
      assertEquals((m.msb << 7) | m.lsb, v)

  test("every channel maps to its status byte"):
    (1 to ChannelCount).foreach: c =>
      assertEquals(Message(c, 0).status, 0xe0 + c - 1)
