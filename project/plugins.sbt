// Fat jar: Bitwig loads the extension with its own class loader, so the Scala
// standard library must be bundled into the .bwextension.
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.5.0")

// BuildInfo.version for the extension definition.
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.13.2")

// scalafmtCheckAll and scalafmtSbtCheck for CI; uses the version in .scalafmt.conf.
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
