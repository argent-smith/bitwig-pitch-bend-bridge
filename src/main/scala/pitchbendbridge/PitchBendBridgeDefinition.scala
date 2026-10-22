// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Pavel Argentov

package pitchbendbridge

import java.util.UUID

import com.bitwig.extension.api.PlatformType
import com.bitwig.extension.controller.{AutoDetectionMidiPortNamesList, ControllerExtensionDefinition}
import com.bitwig.extension.controller.api.ControllerHost

// Found by Bitwig through META-INF/services; needs a public no-arg constructor.
final class PitchBendBridgeDefinition extends ControllerExtensionDefinition:

  override def getName: String = "Pitch Bend Bridge (Scala)"
  override def getAuthor: String = "Pavel Argentov"
  override def getVersion: String = BuildInfo.version

  // Differs from the JavaScript script's UUID so both can be installed side by side.
  override def getId: UUID = UUID.fromString("b8eefa16-25aa-465a-9fbf-abef4b3160f7")

  override def getRequiredAPIVersion: Int = 18
  override def getHardwareVendor: String = "Mostly Random"
  override def getHardwareModel: String = "Pitch Bend Bridge (Scala)"
  override def getNumMidiInPorts: Int = 0
  override def getNumMidiOutPorts: Int = 1

  override def listAutoDetectionMidiPortNames(list: AutoDetectionMidiPortNamesList, platform: PlatformType): Unit = ()

  override def createInstance(host: ControllerHost): PitchBendBridgeExtension =
    PitchBendBridgeExtension(this, host)
