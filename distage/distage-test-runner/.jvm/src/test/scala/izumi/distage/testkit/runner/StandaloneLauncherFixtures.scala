package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit

final class LauncherSuccessSuite extends AnyWordSpec {
  "CLI" should { "run" in { println("CLI_SUCCESS_BODY_STDOUT") } }
}

final class LauncherFailureSuite extends AnyWordSpec {
  "CLI" should { "fail" in { println("CLI_FAILURE_BODY_STDOUT"); throw new IllegalStateException("Expected CLI test failure") } }
}

final class LauncherPlanningFailureSuite extends TestSuite {
  override def register(context: RegistrationContext): RegisteredSuite = {
    val descriptor = SuiteDescriptor(SuiteId(getClass.getName), "LauncherPlanningFailureSuite")
    val test = TestDescriptor(TestId(context.target, descriptor.id, Vector("planning failure"), None), "planning failure", SourceLocation.Unavailable, EffectiveSettings(Vector.empty, memoization = true))
    val provider = new FixtureSupport.Provider {
      override def plan(selected: Vector[TestDescriptor]): scala.concurrent.Future[ExecutionPlan] = scala.concurrent.Future.successful(new FixtureSupport.Plan(selected) {
        override val inspection: PlanInspection = PlanInspection(Vector.empty, Vector.empty, Vector(PlanFailure(tests.map(_.id), RunnerFailure.message(FailurePhase.Planning, "Controlled CLI planning failure"))))
        override def execute(context: RunExecutionContext): scala.concurrent.Future[ProviderOutcome] = { val _ = context; throw new IllegalStateException("CLI inspection must not execute") }
      })
    }
    RegisteredSuite(descriptor, Vector(test), provider)
  }
}

object StandaloneLauncherFixtures {
  private final val TimeoutSeconds = 30L

  private final case class Case(name: String, commands: Vector[ProtocolMessage], suite: String, exit: Int, body: Option[String], successful: Boolean, cancelled: Boolean, rejected: Boolean)

  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Probe requires its capture directory")
    val directory = Paths.get(args(0))
    val identity = CatalogueIdentity(BuildId("cli-build"), BuildTargetId("cli-target"), CatalogueId("cli-catalogue"))
    val run = RunId("cli-run")
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val successSuite = "izumi.distage.testkit.runner.LauncherSuccessSuite"
    val failureSuite = "izumi.distage.testkit.runner.LauncherFailureSuite"
    val planningFailureSuite = "izumi.distage.testkit.runner.LauncherPlanningFailureSuite"
    val selectedId = TestId(identity.target, SuiteId(successSuite), Vector("CLI", "should", "run"), None)
    val selectedRequest = RequestArguments.parse(RequestArguments.render(request.copy(selection = Selection.Only(Vector.empty, Vector(selectedId)), overrides = request.overrides.copy(memoization = MemoizationOverride.Disabled))))
      .fold(error => throw new IllegalArgumentException(error.message), value => value)
    def command(operation: RequestOperation, value: RunRequest): ProtocolMessage = ProtocolMessage.Request(operation, run, value)
    val cases = Vector(
      Case("success", Vector(ProtocolMessage.Discover(run, identity.build, identity.target), command(RequestOperation.Resolve, request), command(RequestOperation.Plan, request), command(RequestOperation.Execute, request)), successSuite, 0, Some("CLI_SUCCESS_BODY_STDOUT"), true, false, false),
      Case("inspection", Vector(command(RequestOperation.Plan, request)), successSuite, 0, None, false, false, false),
      Case("planning-failure", Vector(command(RequestOperation.Plan, request)), planningFailureSuite, 1, None, false, false, false),
      Case("test-failure", Vector(command(RequestOperation.Execute, request)), failureSuite, 1, Some("CLI_FAILURE_BODY_STDOUT"), false, false, false),
      Case("stale-build", Vector(command(RequestOperation.Execute, request.copy(identity = identity.copy(build = BuildId("stale"))))), successSuite, 1, None, false, false, true),
      Case("stale-catalogue", Vector(command(RequestOperation.Execute, request.copy(identity = identity.copy(catalogue = CatalogueId("stale"))))), successSuite, 1, None, false, false, true),
      Case("unknown-id", Vector(command(RequestOperation.Execute, request.copy(selection = Selection.Only(Vector.empty, Vector(TestId(identity.target, SuiteId("unknown"), Vector("missing"), None)))))), successSuite, 1, None, false, false, true),
      Case("cancel-only", Vector(ProtocolMessage.Cancel(run)), successSuite, 1, None, false, false, false),
      Case("pre-cancel", Vector(ProtocolMessage.Cancel(run), command(RequestOperation.Execute, request)), successSuite, 1, None, false, true, false),
      Case("empty", Vector.empty, successSuite, 1, None, false, false, false),
      Case("normalized-selection", Vector(command(RequestOperation.Resolve, selectedRequest), command(RequestOperation.Plan, selectedRequest), command(RequestOperation.Execute, selectedRequest)), successSuite, 0, Some("CLI_SUCCESS_BODY_STDOUT"), true, false, false),
    )
    cases.foreach { test =>
      val capture = Files.createDirectory(directory.resolve(test.name))
      val input = capture.resolve("commands.jsonl")
      val output = capture.resolve("output.jsonl")
      val stdout = capture.resolve("stdout.log")
      val stderr = capture.resolve("stderr.log")
      val bytes = test.commands.map(ProtocolCodec.encode).map(_ + "\n").mkString.getBytes(StandardCharsets.UTF_8)
      write(input, bytes)
      val java = Paths.get(System.getProperty("java.home"), "bin", "java").toString
      val argv = Vector(java, "-cp", System.getProperty("java.class.path"), "izumi.distage.testkit.runner.StandaloneLauncher", identity.build.value, identity.target.value, identity.catalogue.value, input.toString, output.toString, test.suite)
      write(capture.resolve("argv.txt"), argv.mkString("\n").getBytes(StandardCharsets.UTF_8))
      val process = new ProcessBuilder(argv*).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      val terminated = process.waitFor(TimeoutSeconds, TimeUnit.SECONDS)
      if (!terminated) {
        val _ = process.destroyForcibly()
        require(process.waitFor(TimeoutSeconds, TimeUnit.SECONDS), "Timed-out CLI child did not terminate: " + test.name)
      }
      require(terminated, "CLI child timed out: " + test.name)
      require(process.exitValue() == test.exit, "Unexpected CLI exit: " + test.name + " actual=" + process.exitValue())
      val ordinaryOutput = new String(Files.readAllBytes(stdout), StandardCharsets.UTF_8)
      require(test.body.fold(!ordinaryOutput.contains("BODY_STDOUT"))(marker => ordinaryOutput.contains(marker)), "CLI body/stdout mismatch: " + test.name)
      val diagnostics = new String(Files.readAllBytes(stderr), StandardCharsets.UTF_8)
      require(!Vector("RejectedExecutionException", "NoClassDefFoundError", "ClassNotFoundException").exists(diagnostics.contains), "CLI child has an unexpected callback or classloading diagnostic: " + test.name)
      val messages = read(output)
      val completed = messages.collect { case ProtocolMessage.Completed(value) => value }
      require(completed.exists(_.successful) == test.successful && completed.exists(_.cancelled) == test.cancelled, "CLI outcome mismatch: " + test.name)
      require(messages.exists(_.isInstanceOf[ProtocolMessage.Rejected]) == test.rejected, "CLI rejection mismatch: " + test.name)
      completed.foreach { outcome =>
        require(messages.last == ProtocolMessage.Completed(outcome), "CLI terminal frame must be last: " + test.name)
        val events = messages.collect { case value: ProtocolMessage.Event => value }
        require(events.map(_.sequence) == events.indices.map(_.toLong).toVector && events.last.event == RunEvent.Finished(run, outcome), "CLI events must finish contiguously: " + test.name)
      }
      if (test.name == "success") {
        val listed = messages.collect { case ProtocolMessage.Discovered(_, catalogue) => catalogue.tests.map(_.id) }
        val resolved = messages.collect { case ProtocolMessage.Resolved(_, selection) => selection.tests.map(_.id) }
        val planned = messages.collect { case ProtocolMessage.Planned(_, plan) => plan.selection.tests.map(_.id) }
        require(listed.size == 1 && resolved == listed && planned == listed && completed.map(_.results.map(_.id)) == listed, "CLI discovery/resolve/plan/run IDs must agree")
      }
      if (test.name == "test-failure") require(completed.head.results.exists(_.failure.exists(_.message.contains("Expected CLI test failure"))), "CLI must preserve test failure diagnostics")
      if (test.name == "normalized-selection") {
        require(completed.head.results.map(_.id) == Vector(selectedId), "Standalone normalized selection must execute and report exactly the selected ID")
        val resolved = messages.collect { case ProtocolMessage.Resolved(_, selection) => selection }
        val planned = messages.collect { case ProtocolMessage.Planned(_, plan) => plan.selection }
        require(resolved.size == 1 && planned == resolved && resolved.head.request == selectedRequest && resolved.head.tests.map(_.id) == Vector(selectedId) && resolved.head.tests.forall(!_.settings.memoization), "Standalone resolution and plan must retain the normalized request and disabled memoization")
      }
      if (test.name == "planning-failure") require(messages.collect { case ProtocolMessage.Planned(_, plan) => plan.inspection.failures }.exists(_.exists(value => value.failure.phase == FailurePhase.Planning && value.failure.message == "Controlled CLI planning failure")), "CLI must retain planning failure diagnostics on unsuccessful inspection")
      println("STANDALONE_LAUNCHER_CASE_OK name=" + test.name + " exit=" + process.exitValue() + " frames=" + messages.size)
    }
    println("STANDALONE_LAUNCHER_FIXTURES_OK cases=" + cases.size + " processes=actual stdout=separate ids=agreed exits=verified")
  }

  private def write(path: Path, bytes: Array[Byte]): Unit = { val _ = Files.write(path, bytes); () }

  private def read(path: Path): Vector[ProtocolMessage] = {
    val source = FileProtocolFrameSource.open(path)
    try {
      val messages = Vector.newBuilder[ProtocolMessage]
      var next = source.readFrame()
      while (next.nonEmpty) {
        messages += ProtocolCodec.decode(next.get).fold(error => throw new IllegalArgumentException(error.message), value => value)
        next = source.readFrame()
      }
      messages.result()
    } finally source.close()
  }
}
