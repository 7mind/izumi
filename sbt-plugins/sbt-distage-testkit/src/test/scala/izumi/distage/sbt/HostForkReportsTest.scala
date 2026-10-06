package izumi.distage.sbt

import izumi.distage.sbt.target.TaskCompleteness
import izumi.distage.testkit.protocol.{BuildTargetId, Failure, FailurePhase, FileForkRunReports, ForkRunReport, ForkRunReports, ForkSuiteOwner, RunId, RunOutcome, SuiteId, TestId, TestResult as LogicalResult, TestStatus}
import sbt.{TestDefinition, TestEvent, TestResult, TestsListener}
import sbt.testing.{Event, Status, SubclassFingerprint, SuiteSelector, TestSelector}

import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters.*

object HostForkReportsTest {
  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Fork projection checks require a new directory")
    val directory = Files.createDirectory(Paths.get(arguments.head).toAbsolutePath)
    try {
      Seq("memory", "file").foreach { mode =>
        Seq("cancelled", "delivered", "mismatch", "duplicates").foreach { scenario =>
          val child = Files.createDirectory(directory.resolve(mode + "-" + scenario))
          val reports: ForkRunReports = if (mode == "file") new FileForkRunReports(child) else new MemoryReports
          val terminals: TaskCompleteness.CompletionStore = if (mode == "file") new TaskCompleteness.FileCompletionStore(child) else new MemoryTerminals
          contract(mode, scenario, reports, terminals)
        }
      }
    } finally {
      val entries = Files.walk(directory)
      try entries.sorted(java.util.Comparator.reverseOrder()).forEach(path => { val _ = Files.delete(path) }) finally entries.close()
    }
  }

  private def contract(mode: String, scenario: String, reports: ForkRunReports, terminals: TaskCompleteness.CompletionStore): Unit = {
    val name = HostSuiteName("fixture.ActualSuite")
    val logical = SuiteId("logical-identity-differs-from-class-name")
    val fingerprint = new SubclassFingerprint {
      override def isModule(): Boolean = false
      override def superclassName(): String = "izumi.distage.testkit.runner.TestSuite"
      override def requireNoArgConstructor(): Boolean = true
    }
    val definition = new TestDefinition(name.value, fingerprint, false, Array(new SuiteSelector))
    val results = (1 to 3).map(index => LogicalResult(TestId(BuildTargetId("target"), logical, Vector("same name", index.toString), None), TestStatus.Cancelled, None, 1000000L)).toVector
    val failure = Failure(FailurePhase.Finalization, "fixture.ReleaseFailure", "release failed", Vector.empty, Vector.empty, None, Vector.empty, Vector.empty)
    val report = ForkRunReport(RunOutcome(RunId(UUID.randomUUID().toString), results, Vector(failure), cancelled = true), Vector(ForkSuiteOwner(name.value, Some(logical))))
    reports.publish(report)
    val canceled = if (scenario == "mismatch") 2 else 3
    terminals.publish(new TaskCompleteness.Completion(UUID.randomUUID(), new TaskCompleteness.SuiteName(name.value), ProcessHandle.current().pid(), true, new TaskCompleteness.Counts(0, 0, 1, 0, 0, canceled, 0)))
    if (scenario == "duplicates") reports.publish(report.copy(outcome = report.outcome.copy(run = RunId(UUID.randomUUID().toString))))
    val receipt = new HostReceipt
    receipt.expect(name)
    val received = receipt.configure(Set(name))
    var events = Vector.empty[Event]
    var groups = 0
    val listener = new TestsListener {
      override def doInit(): Unit = ()
      override def doComplete(result: TestResult): Unit = ()
      override def startGroup(value: String): Unit = { require(value == name.value); groups += 1 }
      override def testEvent(value: TestEvent): Unit = events ++= value.detail
      override def endGroup(value: String, cause: Throwable): Unit = throw cause
      override def endGroup(value: String, result: TestResult): Unit = require(value == name.value && result == TestResult.Error)
    }
    if (scenario == "delivered") {
      received.startGroup(name.value)
      received.testEvent(TestEvent(Vector.empty))
      received.endGroup(name.value, TestResult.Error)
    }
    val projection = new HostForkReports(reports, terminals, Seq(definition), Seq(received, listener), receipt)
    if (Set("mismatch", "duplicates").contains(scenario)) {
      try { projection.cancel(Set(name)); throw new AssertionError("Invalid fork projection accepted") }
      catch { case _: IllegalArgumentException => () }
      require(groups == 0 && events.isEmpty, "Invalid fork reports emitted host callbacks")
    } else {
      projection.cancel(Set(name))
      projection.cancel(Set(name))
      if (scenario == "delivered") require(groups == 0 && events.isEmpty, "Completed host group was replayed")
      else {
        require(groups == 1 && events.size == 4 && events.count(_.status() == Status.Canceled) == 3)
        require(events.take(3).map(_.selector().asInstanceOf[TestSelector].testName()) == Vector("same name 1", "same name 2", "same name 3"), "Logical suite binding or selectors changed")
        require(events.last.status() == Status.Error && events.last.throwable().get().getMessage.contains("Finalization: fixture.ReleaseFailure: release failed"))
        require(!receipt.unreported(name), "Projected group did not complete")
      }
    }
    println("HOST_FORK_REPORT_CHECK_OK " + mode + " " + scenario)
  }

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
