// Scala port of PitchBendBridge.control.js as a Bitwig Java extension.
//
//   sbt test       run the unit tests
//   sbt assembly   build target/PitchBendBridge.bwextension

ThisBuild / scalaVersion := "3.8.4"

lazy val root = (project in file("."))
  .settings(
    name := "pitch-bend-bridge",
    version := "0.1",
    resolvers += "Bitwig" at "https://maven.bitwig.com",
    libraryDependencies ++= Seq(
      // Provided by Bitwig at runtime; kept out of the assembled jar.
      "com.bitwig" % "extension-api" % "18" % Provided,
      "org.scalameta" %% "munit" % "1.3.5" % Test
    ),
    // Bitwig 5 ships Java 21, Bitwig 6 ships Java 25; Scala 3.8 needs 17+.
    scalacOptions ++= Seq(
      "-release",
      "17",
      "-deprecation",
      "-feature",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Wsafe-init"
    ),
    assembly / assemblyJarName := "PitchBendBridge.bwextension",
    assembly / assemblyOutputPath := target.value / (assembly / assemblyJarName).value
  )
