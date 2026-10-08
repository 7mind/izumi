import izumi.distage.sbt.DistageTestkitPlugin.autoImport.*
ThisBuild / scalaVersion := sys.props("coverage.scala")
ThisBuild / organization := "izumi.local.coverage"
ThisBuild / version := sys.props("coverage.version")
ThisBuild / scalacOptions ++= (if (scalaVersion.value.startsWith("3.")) Seq("-Ybackend-parallelism", "1", "-Xmax-inlines:64") else Seq("-Xsource:3"))
val coveragePlatform = sys.props("coverage.platform")
val sdkPlugin: AutoPlugin = coveragePlatform match {
  case "jvm" => sbt.plugins.JvmPlugin
  case "js" => ScalaJSPlugin
  case "native" => ScalaNativePlugin
}
val hostPlugin: AutoPlugin = coveragePlatform match {
  case "jvm" => DistageTestkitPlugin
  case "js" => DistageTestkitJsPlugin
  case "native" => DistageTestkitNativePlugin
}
val runtimeSuffix = coveragePlatform match {
  case "jvm" => ""
  case "js" => "_sjs1"
  case "native" => "_native0.5"
}
val platformSettings = coveragePlatform match {
  case "jvm" => Seq.empty
  case "js" => Seq(scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)))
  case "native" => Seq(nativeConfig ~= (_.withMultithreading(true)))
}
val compilerSettings = if (coveragePlatform == "jvm") Seq.empty else Seq(
  coverageEnabled := (ThisBuild / coverageEnabled).value && scalaVersion.value.startsWith("2."),
  libraryDependencies := ScoverageCompilerDependencies.forPlatform(libraryDependencies.value, scalaVersion.value, scalaBinaryVersion.value),
) ++ Seq(Compile, Test).map { configuration =>
  configuration / compile / scalacOptions ++= Def.uncached {
    val converter = fileConverter.value
    if (coverageEnabled.value && scalaVersion.value.startsWith("2.")) Seq("-Ymacro-classpath:" + ScoverageCompilerDependencies.macroClasspath((configuration / dependencyClasspath).value.map(entry => converter.toPath(entry.data).toFile), update.value.matching(configurationFilter(scoverage.ScoverageSbtPlugin.ScoveragePluginConfig.name)), scalaBinaryVersion.value)) else Seq.empty
  }
}
val runnerSettings = Seq(
  libraryDependencySchemes += "org.scala-native" % "test-interface_native0.5_2.13" % "always",
  libraryDependencies += "io.7mind.izumi" % ("distage-test-runner" + runtimeSuffix + "_" + scalaBinaryVersion.value) % sys.props("coverage.artifact-version") % Test,
  Test / testFrameworks := Seq(TestFramework("izumi.distage.testkit.runner.bootstrap.Framework")),
  Test / fork := sys.props("coverage.fork").toBoolean,
  Test / distageBuildId := "coverage-fixture",
  Test / distageTargetId := name.value,
  Test / distageCatalogueId := name.value,
  Test / testListeners += new sbt.JUnitXmlTestsListener((target.value / "explicit").getAbsolutePath),
)
lazy val witness = project.in(file("witness")).enablePlugins(sdkPlugin).settings(platformSettings ++ compilerSettings).settings(
  libraryDependencies += "io.7mind.izumi" % ("fundamentals-assertions" + runtimeSuffix + "_" + scalaBinaryVersion.value) % sys.props("coverage.artifact-version"),
)
lazy val left = project.in(file("left")).enablePlugins(sdkPlugin, hostPlugin).settings(platformSettings ++ compilerSettings).dependsOn(witness).settings(runnerSettings)
lazy val right = project.in(file("right")).enablePlugins(sdkPlugin, hostPlugin).settings(platformSettings ++ compilerSettings).dependsOn(witness).settings(runnerSettings)
lazy val root = project.in(file(".")).aggregate(witness, left, right).settings(publish / skip := true)

val captureCoverage = taskKey[Unit]("Preserve coverage and body reports before the normal clean publication")
captureCoverage := Def.uncached {
  val rootDirectory = baseDirectory.value
  val reports = (rootDirectory ** "scoverage.xml").get()
  require(reports.nonEmpty, "Coverage did not generate any XML reports")
  val destination = rootDirectory / "coverage-capture"
  require(!destination.exists(), "Coverage capture already exists")
  reports.foreach { report =>
    val relative = IO.relativize(rootDirectory, report).getOrElse(throw new IllegalStateException("Coverage report is outside the fixture"))
    IO.copyFile(report, destination / relative)
  }
  println("COVERAGE_CAPTURED reports=" + reports.size)
}

captureCoverage / aggregate := false
