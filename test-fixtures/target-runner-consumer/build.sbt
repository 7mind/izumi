import sbt.complete.DefaultParsers.spaceDelimited
ThisBuild / scalaVersion := sys.props("candidate.scala")
val fixtureRoot = file(".").getCanonicalFile
val candidatePlatform = settingKey[String]("Target dependency suffix")
val armCandidateInterruption = taskKey[Unit]("Interrupt active SDK execute after a target start")
val disarmCandidateInterruption = taskKey[Unit]("Disarm the SDK interruption trigger")
val expectCandidateCancellation = taskKey[Unit]("Require a failed cancellation command without ending the SBT session")
val collectCandidate = inputKey[Unit]("Capture exact per-command suite XML")
val common = Seq(
  disarmCandidateInterruption := Def.uncached { val _ = System.setProperty("candidate.interrupt", "false"); require(System.getProperty("candidate.interrupt") == "false", "Interruption remains armed"); println("SDK_INTERRUPTION_DISARMED") },
  expectCandidateCancellation := Def.uncached {
    val attempted = (Test / testFull).result.value
    attempted match {
      case Result.Inc(_) => println("SDK_EXPECTED_CANCELLATION_FAILURE")
      case Result.Value(_) => throw new IllegalStateException("Cancelled command unexpectedly succeeded")
    }
  },
  armCandidateInterruption := Def.uncached { val _ = System.setProperty("candidate.interrupt", "true"); require(System.getProperty("candidate.interrupt") == "true", "Interruption was not armed"); println("SDK_INTERRUPTION_ARMED") },
  collectCandidate := {
    val label = spaceDelimited("case").parsed
    require(label.size == 1, "Expected one target case name")
    val reports = target.value / "explicit" / "test-reports"
    val destination = file(sys.props("candidate.captures")) / candidatePlatform.value / label.head
    require(!destination.exists(), "Target report capture already exists")
    IO.copyDirectory(reports, destination)
    IO.delete(reports)
  },
  Test / unmanagedSourceDirectories += fixtureRoot / "shared",
  Test / testFrameworks := Seq(TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")),
  libraryDependencies += "io.7mind.izumi" % ("distage-test-runner_" + candidatePlatform.value + "_" + scalaBinaryVersion.value) % sys.props("fixture.artifact-version") % Test,
  Test / testOptions += Tests.Argument(TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"), "--build-id", "candidate", "--target-id", "candidate-" + candidatePlatform.value, "--catalogue-id", "candidate"),
  Test / parallelExecution := true,
  Test / testListeners += new sbt.JUnitXmlTestsListener((target.value / "explicit").getAbsolutePath)
)
lazy val js = project.in(file("js")).enablePlugins(ScalaJSPlugin, TransportJsProjectionPlugin).settings(common).settings(
  candidatePlatform := "sjs1",
  Test / unmanagedSourceDirectories += fixtureRoot / "platform-js",
  Test / scalacOptions ++= (if (scalaVersion.value.startsWith("2.")) Seq("-Xsource:3") else Seq("-Ybackend-parallelism", "1"))
)
lazy val native = project.in(file("native")).enablePlugins(ScalaNativePlugin, TransportNativeProjectionPlugin).settings(common).settings(
  candidatePlatform := "native0.5",
  Test / unmanagedSourceDirectories += fixtureRoot / "platform-native",
  Test / scalacOptions ++= (if (scalaVersion.value.startsWith("2.")) Seq("-Xsource:3") else Seq("-Ybackend-parallelism", "1")),
  Test / nativeConfig ~= (_.withMultithreading(true).withBaseName("distage-target-candidate"))
)

Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, sys.props("fixture.host-threads").toInt))
