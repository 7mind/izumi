package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit

object InspectionLauncherFixtures {
  private final val TimeoutSeconds = 30L
  private final case class Case(name: String, operation: String, request: RunRequest, suite: String, exit: Int, rejected: Boolean)

  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Inspection fixture requires its capture directory")
    val directory = Paths.get(arguments(0))
    val identity = CatalogueIdentity(BuildId("inspection-build"), BuildTargetId("inspection-target"), CatalogueId("inspection-catalogue"))
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val suite = "izumi.distage.testkit.runner.bootstrap.BootstrapArgumentSuite"
    val selectedId = TestId(identity.target, SuiteId(suite), Vector("a", "b c"), Some("selected"))
    val selected = request.copy(selection = Selection.Only(Vector.empty, Vector(selectedId)), overrides = RunOverrides(
      Vector(AxisChoice(AxisId("repo"), AxisValue("real"))), Vector(AxisChoice(AxisId("mode"), AxisValue("enabled"))), MemoizationOverride.Disabled,
    ))
    val cases = Vector(
      Case("list", "list", request, "izumi.distage.testkit.runner.LauncherSuccessSuite", 0, false),
      Case("plan", "plan", request, "izumi.distage.testkit.runner.LauncherSuccessSuite", 0, false),
      Case("selected-list", "list", selected, suite, 0, false),
      Case("selected-plan", "plan", selected, suite, 0, false),
      Case("unknown", "list", selected.copy(selection = Selection.Only(Vector.empty, Vector(selectedId.copy(variant = Some("unknown"))))), suite, 1, true),
      Case("planning-failure", "plan", request, "izumi.distage.testkit.runner.LauncherPlanningFailureSuite", 1, false),
    )
    cases.foreach { test =>
      val capture = Files.createDirectory(directory.resolve(test.name))
      val stdout = capture.resolve("stdout.log")
      val stderr = capture.resolve("stderr.log")
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toString
      val argv = Vector(java, "-cp", System.getProperty("java.class.path"), "izumi.distage.testkit.runner.InspectionLauncher", test.operation) ++ RequestArguments.render(test.request) ++ Vector("--", test.suite)
      val _ = Files.write(capture.resolve("argv.txt"), argv.mkString("\n").getBytes(StandardCharsets.UTF_8))
      val process = new ProcessBuilder(argv*).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      val terminated = process.waitFor(TimeoutSeconds, TimeUnit.SECONDS)
      if (!terminated) {
        val _ = process.destroyForcibly()
        require(process.waitFor(TimeoutSeconds, TimeUnit.SECONDS), "Timed-out inspection child did not terminate")
      }
      require(terminated && process.exitValue() == test.exit, "Unexpected inspection exit: " + test.name)
      val raw = new String(Files.readAllBytes(stdout), StandardCharsets.UTF_8)
      require(!raw.contains("BODY_STDOUT") && !raw.contains("BOOTSTRAP_ARGUMENT_BODY_OK"), "Inspection executed a body: " + test.name)
      val frames = raw.linesIterator.filter(_.startsWith("DISTAGE_INSPECTION ")).map(_.stripPrefix("DISTAGE_INSPECTION ")).toVector
      require(frames.size == 1, "Inspection must emit exactly one response: " + test.name)
      val message = ProtocolCodec.decode(frames.head).fold(error => throw new IllegalArgumentException(error.message), value => value)
      require(message.isInstanceOf[ProtocolMessage.Rejected] == test.rejected, "Inspection rejection mismatch: " + test.name)
      val resolved = message match {
        case ProtocolMessage.Resolved(_, selection) => Some(selection)
        case ProtocolMessage.Planned(_, plan) =>
          require(plan.inspection.validate(plan.selection.tests.map(_.id)).isRight, "Inspection plan identity set is invalid")
          require(plan.inspection.failures.nonEmpty == (test.name == "planning-failure"), "Inspection planning failures must remain visible")
          Some(plan.selection)
        case ProtocolMessage.Rejected(_, failure) =>
          require(failure.phase == FailurePhase.Selection && failure.message.contains("Unknown explicit identities"), "Inspection must preserve unknown-ID selection diagnostics")
          None
        case _ => throw new IllegalStateException("Unexpected inspection message")
      }
      resolved.foreach { selection =>
        require(selection.request == test.request, "Inspection changed the normalized request")
        if (test.name.startsWith("selected-")) require(selection.tests.map(_.id) == Vector(selectedId) && selection.tests.forall(!_.settings.memoization), "Inspection must retain the structured path, variant and effective settings")
      }
      val diagnostics = new String(Files.readAllBytes(stderr), StandardCharsets.UTF_8)
      require(!Vector("RejectedExecutionException", "NoClassDefFoundError", "ClassNotFoundException").exists(diagnostics.contains), "Inspection has an unexpected callback or classloading diagnostic")
      println("INSPECTION_LAUNCHER_CASE_OK name=" + test.name + " exit=" + process.exitValue())
    }
    println("INSPECTION_LAUNCHER_FIXTURES_OK cases=" + cases.size + " processes=actual bodies=zero ids=structured failures=retained")
  }
}
