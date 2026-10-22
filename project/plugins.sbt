// Fat jar: Bitwig loads the extension with its own class loader, so the Scala
// standard library must be bundled into the .bwextension.
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.4.2")
