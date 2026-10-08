ThisBuild / scalaVersion := sys.props("izumi.fixture.scala-version")
ThisBuild / organization := "izumi.local.fixtures"
ThisBuild / version := "0.0.0"
ThisBuild / publish / skip := true

val captureJUnit = taskKey[Unit]("Capture reports from the actual project target")

val common = Seq(
  Test / test / testExecution := Def.uncached {
    val execution = (Test / test / testExecution).value
    val listeners = execution.options.collect { case Tests.Listeners(values) => values }.flatten
    require(listeners.exists(_.getClass.getName == "izumi.distage.sbt.HostJUnitReportListener"), "The production host JUnit wrapper is absent")
    streams.value.log.info("JUNIT_REPORTER_PIPELINE_OK project=" + name.value + " listener=izumi.distage.sbt.HostJUnitReportListener")
    execution
  },
  captureJUnit := Def.uncached {
    val reports = target.value / "test-reports"
    val destination = file(sys.props("izumi.fixture.captures")) / name.value
    require(reports.isDirectory, "Production JUnit report directory is absent")
    require(!destination.exists(), "Report capture must be new")
    IO.copyDirectory(reports, destination)
  },
  libraryDependencies += "io.7mind.izumi" %% "distage-testkit-runner" % sys.props("izumi.fixture.version") % Test,
  scalacOptions ++= (if (scalaVersion.value.startsWith("3.")) Seq("-Yretain-trees", "-Xmax-inlines:64", "-Ybackend-parallelism", "1") else Seq("-Xsource:3")),
  Test / fork := true,
  Test / testFrameworks := Seq(TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")),
)

lazy val parallel = project.enablePlugins(izumi.distage.sbt.DistageTestkitPlugin).settings(common)
lazy val checks = project.enablePlugins(izumi.distage.sbt.DistageTestkitPlugin).settings(common).settings(
  Test / javaOptions += "-Dizumi.junit.report=" + (parallel / target).value.getAbsolutePath + "/test-reports/TEST-izumi.distage.testkit.reporting.ParallelSleepSuite.xml",
  Test / testFull := Def.uncached { (Test / testFull).dependsOn(parallel / Test / testFull).value },
)
lazy val root = project.in(file(".")).aggregate(checks).settings(publish / skip := true)
