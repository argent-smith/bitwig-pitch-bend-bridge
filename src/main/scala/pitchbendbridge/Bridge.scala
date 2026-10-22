// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Pavel Argentov

package pitchbendbridge

// The functional core: Bitwig events go in, commands come out. Nothing here
// touches the Bitwig API; the extension only feeds events to `step` and
// executes the returned commands.
object Bridge:

  final val BendRange = 16384 // 14 bits: 0..16383
  final val BendCenter = 8192
  final val RemoteCount = 8
  final val ChannelCount = 16

  // Setting defaults and limits, shared with the extension's settings.
  final val DefaultChannel = 1
  final val DefaultDeadband = 2
  final val MaxDeadband = 64

  // A Pitch Bend message. `apply` clamps the channel and the value; the MIDI
  // bytes are derived from them, so they cannot disagree.
  final case class Message private (channel: Int, value: Int):
    def status: Int = 0xe0 | (channel - 1)
    def lsb: Int = value & 0x7f
    def msb: Int = (value >> 7) & 0x7f

  object Message:
    def apply(channel: Int, value: Int): Message =
      new Message(clamp(1, ChannelCount)(channel), clamp(0, BendRange - 1)(value))

  final case class State(
      channel: Int = DefaultChannel,
      remoteIndex: Int = 0, // 0-based
      deadband: Int = DefaultDeadband,
      lastSent: Option[Int] = None,
      latest: Map[Int, Int] = Map.empty // latest value of each Remote Control
  )

  enum Event:
    case RemoteChanged(index: Int, value: Int)
    case DeviceExists(exists: Boolean)
    case ChannelChanged(channel: Int)
    case DeadbandChanged(steps: Int)
    case SourceChanged(remoteNumber: Int) // 1-based, as shown in settings
    case Pinned(trackName: String, deviceName: String)
    case Unpinned
    case ResetPressed
    case Exited

  // What the extension must do in Bitwig, in order.
  enum Command:
    case Send(message: Message)
    case PinCursors
    case FollowSelection // unpins the cursors
    case Popup(text: String)

  // -------------------------------------------------------------------------
  // Pure functions
  // -------------------------------------------------------------------------

  def clamp(lo: Int, hi: Int)(x: Int): Int = math.max(lo, math.min(hi, x))

  // Snap values within `deadband` steps of the center to the exact center.
  def snapToCenter(deadband: Int)(v: Int): Int =
    if math.abs(v - BendCenter) <= deadband then BendCenter else v

  // -------------------------------------------------------------------------
  // Steps: state transitions that may produce commands
  // -------------------------------------------------------------------------

  type Step = State => (State, Vector[Command])

  extension (f: Step)
    def >>(g: Step): Step = s0 =>
      val (s1, out1) = f(s0)
      val (s2, out2) = g(s1)
      (s2, out1 ++ out2)

  private def modify(f: State => State): Step = s => (f(s), Vector.empty)

  // Chooses the next step by the current state.
  private def withState(f: State => Step): Step = s => f(s)(s)

  private val noop: Step = modify(identity)

  private def tell(command: Command): Step = s => (s, Vector(command))

  // Drops a value equal to the last one sent.
  private def send(msg: Message): Step = s =>
    if s.lastSent.contains(msg.value) then (s, Vector.empty)
    else (s.copy(lastSent = Some(msg.value)), Vector(Command.Send(msg)))

  private val forget: Step = modify(_.copy(lastSent = None))

  private def emit(raw: Int): Step =
    withState(s => send(Message(s.channel, snapToCenter(s.deadband)(raw))))

  private val resetBend: Step = forget >> withState(s => send(Message(s.channel, BendCenter)))

  private val reEmitCurrent: Step = forget >> withState(s => s.latest.get(s.remoteIndex).fold(noop)(emit))

  def transition(event: Event): Step =
    import Event.*, Command.*
    event match
      case RemoteChanged(index, value) =>
        modify(s => s.copy(latest = s.latest.updated(index, value))) >>
          withState(s => if index == s.remoteIndex then emit(value) else noop)
      case DeviceExists(exists) =>
        if exists then noop else resetBend
      case ChannelChanged(channel) =>
        // Release the bend on the old channel first.
        resetBend >> modify(_.copy(channel = channel)) >> reEmitCurrent
      case DeadbandChanged(steps) =>
        modify(_.copy(deadband = steps))
      case SourceChanged(remoteNumber) =>
        modify(_.copy(remoteIndex = clamp(0, RemoteCount - 1)(remoteNumber - 1))) >> reEmitCurrent
      case Pinned(trackName, deviceName) =>
        tell(PinCursors) >> tell(Popup(s"PB Bridge → $trackName / $deviceName")) >> reEmitCurrent
      case Unpinned =>
        tell(FollowSelection) >> resetBend >> tell(Popup("PB Bridge: follows selection"))
      case ResetPressed | Exited =>
        resetBend

  def step(state: State, event: Event): (State, Vector[Command]) = transition(event)(state)
