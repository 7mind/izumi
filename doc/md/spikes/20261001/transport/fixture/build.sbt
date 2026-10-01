ThisBuild / scalaVersion := "3.3.7"
val fixtureRoot = file(".").getCanonicalFile
@transient lazy val suiteResults = taskKey[Unit]("Print each suite's host Tests.Output result")
val common = Seq(
  Test / unmanagedSourceDirectories += fixtureRoot / "shared",
  Test / testFrameworks := Seq(new TestFramework("transport.TransportFramework")),
  Test / parallelExecution := true,
  Test / testListeners += new sbt.JUnitXmlTestsListener((target.value / "explicit").getAbsolutePath),
  suiteResults := {
    val output = (Test / executeTests).value
    output.events.toSeq.sortBy(_._1).foreach { case (suite, result) =>
      println("SUITE_RESULT " + suite + " " + result.result + " passed=" + result.passedCount +
        " failed=" + result.failureCount + " errors=" + result.errorCount)
    }
    println("OVERALL_RESULT " + output.overall + " suites=" + output.events.size)
  }
)
val reportProjects = Seq("js", "native")
// Moves the explicit JUnit reports written since the previous step into target/spike-collect/<label>.
commands += Command.single("spikeCollect") { (state, label) =>
  val extracted = Project.extract(state)
  val destination = fixtureRoot / "target" / "spike-collect" / label
  reportProjects.foreach { name =>
    val reports = extracted.get(LocalProject(name) / target) / "explicit" / "test-reports"
    println("SPIKE_REPORT_DIR " + name + " " + reports.getAbsolutePath)
    IO.listFiles(reports).filter(_.getName.endsWith(".xml")).foreach { report =>
      IO.copyFile(report, destination / name / report.getName)
      IO.delete(report)
    }
  }
  println("SPIKE_COLLECT " + label)
  state
}
lazy val js = project.in(file("js")).enablePlugins(ScalaJSPlugin, TransportJsProjectionPlugin).settings(common).settings(
  Test / unmanagedSourceDirectories += fixtureRoot / "js"
)
lazy val native = project.in(file("native")).enablePlugins(ScalaNativePlugin, TransportNativeProjectionPlugin).settings(common).settings(
  Test / unmanagedSourceDirectories += fixtureRoot / "native",
  Test / nativeConfig := (Test / nativeConfig).value.withBaseName("transport-stub")
)

val appCommon = Seq(
  Compile / unmanagedSourceDirectories += fixtureRoot / "shared",
  Compile / mainClass := Some("transport.Main")
)
lazy val jsApp = project.in(file("js-app")).enablePlugins(ScalaJSPlugin).settings(appCommon).settings(
  Compile / unmanagedSourceDirectories += fixtureRoot / "js",
  libraryDependencies += "org.scala-js" % "scalajs-test-interface_2.13" % "1.22.0",
  scalaJSUseMainModuleInitializer := true,
  Compile / scalaJSModuleInitializers := Seq(org.scalajs.linker.interface.ModuleInitializer.mainMethodWithArgs(
    "transport.Main", "main", List("transport.SuiteA", "transport.SuiteB")))
)
lazy val nativeApp = project.in(file("native-app")).enablePlugins(ScalaNativePlugin).settings(appCommon).settings(
  Compile / unmanagedSourceDirectories += fixtureRoot / "native",
  libraryDependencies += "org.scala-native" % "test-interface_native0.5_3" % "0.5.12"
)

lazy val jsCom = project.in(file("js-com")).enablePlugins(ScalaJSPlugin).settings(appCommon).settings(
  Compile / unmanagedSourceDirectories += fixtureRoot / "js",
  Compile / unmanagedSourceDirectories += fixtureRoot / "com",
  libraryDependencies += "org.scala-js" % "scalajs-test-interface_2.13" % "1.22.0",
  Compile / mainClass := Some("transport.ComMain"),
  scalaJSUseMainModuleInitializer := true
)
@transient lazy val launchJsCom = taskKey[Unit]("Exercise the public JSEnv communication transport")
launchJsCom / aggregate := false
launchJsCom := {
  val env = (jsCom / Compile / jsEnv).value
  val input = (jsCom / Compile / jsEnvInput).value
  val messages = new java.util.concurrent.ConcurrentLinkedQueue[String]()
  val terminal = scala.concurrent.Promise[String]()
  val config = org.scalajs.jsenv.RunConfig().withInheritOut(true).withInheritErr(true)
  val run = env.startWithCom(input, config, message => {
    println("COM " + message)
    messages.add(message)
    if (message.startsWith("END ")) terminal.success(message)
  })
  try {
    run.send("SELECT transport.SuiteA,transport.SuiteB")
    val result = scala.concurrent.Await.result(terminal.future, scala.concurrent.duration.Duration(30, "seconds"))
    require(result == "END bodies=6 acquire=1 release=1", "terminal accounting")
    require(messages.size() == 7, "six events plus one terminal")
    run.send("QUIT")
    scala.concurrent.Await.result(run.future, scala.concurrent.duration.Duration(30, "seconds"))
    println("COM_CHECK messages=7 bodies=6 acquire=1 release=1")
  } finally run.close()
}
