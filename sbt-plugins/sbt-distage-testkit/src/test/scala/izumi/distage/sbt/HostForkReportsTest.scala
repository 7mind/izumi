package izumi.distage.sbt

import izumi.distage.sbt.target.TaskCompleteness
import izumi.distage.testkit.protocol.{BuildTargetId, Failure, FailurePhase, FileForkRunReports, ForkProcessId, ForkRunReport, ForkRunReports, ForkSuiteOwner, ProjectedFailure, RunId, RunOutcome, SuiteId, TestId, TestResult as LogicalResult, TestStatus}
import sbt.{TestDefinition, TestEvent, TestResult}
import sbt.testing.{Event, OptionalThrowable, Selector, Status, SuiteSelector, TestSelector}

import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters.*

object HostForkReportsTest {
  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Fork projection checks require a new directory")
    val directory = Files.createDirectory(Paths.get(arguments.head).toAbsolutePath)
    try {
      Seq("memory", "file").foreach { mode =>
        Seq("cancelled", "delivered", "mismatch", "duplicates", "repeated", "partial", "selector-mismatch", "duration-mismatch", "failure-mismatch", "in-process", "unknown-process", "terminal-process", "group-counts", "active-group").foreach { scenario =>
          val child = Files.createDirectory(directory.resolve(mode + "-" + scenario))
          val reports: ForkRunReports = if (mode == "file") new FileForkRunReports(child) else new MemoryReports
          val terminals: TaskCompleteness.CompletionStore = if (mode == "file") new TaskCompleteness.FileCompletionStore(child) else new MemoryTerminals
          contract(mode, scenario, reports, terminals)
        }
      }
    } finally {
      SdkFixtures.deleteTree(directory)
    }
  }

  private def contract(mode: String, scenario: String, reports: ForkRunReports, terminals: TaskCompleteness.CompletionStore): Unit = {
    val name = HostSuiteName("fixture.ActualSuite")
    val logical = SuiteId("logical-identity-differs-from-class-name")
    val fingerprint = SdkFixtures.subclass(false, "izumi.distage.testkit.runner.TestSuite", true)
    val definition = new TestDefinition(name.value, fingerprint, false, Array(new SuiteSelector))
    val results = (1 to 3).map(index => LogicalResult(TestId(BuildTargetId("target"), logical, Vector("same name", index.toString), None), TestStatus.Cancelled, None, 1000000L)).toVector
    val failure = Failure(FailurePhase.Finalization, "fixture.ReleaseFailure", "release failed", Vector.empty, Vector.empty, None, Vector.empty, Vector.empty)
    val host = ForkProcessId(1L)
    val first = ForkProcessId(101L)
    val second = ForkProcessId(102L)
    val report = ForkRunReport(first, RunOutcome(RunId(UUID.randomUUID().toString), results, Vector(failure), cancelled = true), Vector(ForkSuiteOwner(name.value, Some(logical))))
    reports.publish(report)
    def terminal(process: ForkProcessId, canceled: Int): Unit = terminals.publish(new TaskCompleteness.Completion(UUID.randomUUID(), new TaskCompleteness.SuiteName(name.value), process.value, true, new TaskCompleteness.Counts(0, 0, 1, 0, 0, canceled, 0)))
    terminal(first, if (Set("mismatch", "group-counts").contains(scenario)) 2 else 3)
    val repeated = Set("repeated", "partial", "group-counts").contains(scenario)
    if (Set("duplicates", "unknown-process", "in-process").contains(scenario) || repeated) {
      val process = if (scenario == "duplicates") first else if (scenario == "unknown-process") ForkProcessId(999L) else if (scenario == "in-process") host else second
      reports.publish(report.copy(process = process, outcome = report.outcome.copy(run = RunId(UUID.randomUUID().toString))))
      if (scenario != "duplicates") terminal(process, if (scenario == "group-counts") 4 else 3)
    }
    if (scenario == "terminal-process") terminal(ForkProcessId(999L), 3)
    val receipt = new HostReceipt(new izumi.distage.sbt.target.TaskGroups.MemoryStore)
    receipt.expect(name)
    val received = receipt.configure(Set(name))
    val listener = new HostFixtures.RecordedGroups(name, TestResult.Error)
    val delivered = Set("delivered", "partial", "selector-mismatch", "duration-mismatch", "failure-mismatch", "in-process", "active-group").contains(scenario)
    if (delivered) {
      val expectedEvents = (1 to 3).map { index =>
        val testName = if (scenario == "selector-mismatch" && index == 1) "other test" else new String("same name " + index)
        fixtureEvent(definition, new TestSelector(testName), Status.Canceled, None, if (scenario == "duration-mismatch") 2L else 1L)
      }.toVector
      val original = ProjectedFailure.root(failure)
      val cause = if (scenario == "failure-mismatch") new IllegalStateException("different failure") else if (scenario == "in-process") original else new Exception(original.getClass.getName + ": " + original.getMessage)
      val suiteError = fixtureEvent(definition, new SuiteSelector, Status.Error, Some(cause), 0L)
      received.startGroup(name.value)
      received.testEvent(TestEvent((expectedEvents :+ suiteError).reverse))
      if (scenario != "active-group") received.endGroup(name.value, TestResult.Error)
    }
    val admissions = Vector(HostForkAdmission(first, Set(name))) ++ (if (repeated) Vector(HostForkAdmission(second, Set(name))) else Vector.empty)
    val projection = new HostForkReports(reports, terminals, Seq(definition), Seq(received, listener), receipt, host)
    if (Set("mismatch", "duplicates", "selector-mismatch", "duration-mismatch", "failure-mismatch", "unknown-process", "terminal-process", "group-counts", "active-group").contains(scenario)) {
      HostFixtures.rejects(projection.cancel(admissions))
      require(listener.groups == 0 && listener.events.isEmpty, "Invalid fork reports emitted host callbacks")
    } else {
      projection.cancel(admissions)
      projection.cancel(admissions)
      if (scenario == "delivered") require(listener.groups == 0 && listener.events.isEmpty, "Completed host group was replayed")
      else {
        val expectedGroups = if (scenario == "repeated") 2 else 1
        require(listener.groups == expectedGroups && listener.events.size == expectedGroups * 4 && listener.events.count(_.status() == Status.Canceled) == expectedGroups * 3)
        require(listener.events.grouped(4).forall(group => group.take(3).map(_.selector().asInstanceOf[TestSelector].testName()) == Vector("same name 1", "same name 2", "same name 3")), "Logical suite binding or selectors changed")
        require(listener.events.last.status() == Status.Error && listener.events.last.throwable().get().getMessage.contains("Finalization: fixture.ReleaseFailure: release failed"))
        require(!receipt.unreported(name), "Projected group did not complete")
      }
    }
    println("HOST_FORK_REPORT_CHECK_OK " + mode + " " + scenario)
  }

  private def fixtureEvent(definition: TestDefinition, selected: Selector, result: Status, cause: Option[Throwable], elapsed: Long): Event =
    SdkFixtures.event(definition.name, definition.fingerprint, selected, result, cause.fold(new OptionalThrowable)(new OptionalThrowable(_)), elapsed)

  private final class MemoryReports extends ForkRunReports {
    private var values = Vector.empty[ForkRunReport]
    override def publish(value: ForkRunReport): Unit = values :+= value
    override def completed(): Vector[ForkRunReport] = values
  }
  private final class MemoryTerminals extends TaskCompleteness.CompletionStore {
    private var values = Vector.empty[TaskCompleteness.Completion]
    override def publish(value: TaskCompleteness.Completion): Unit = values :+= value
    override def completed(): java.util.List[TaskCompleteness.Completion] = values.asJava
  }
}
