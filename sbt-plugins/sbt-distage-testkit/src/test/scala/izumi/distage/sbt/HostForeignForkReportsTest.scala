package izumi.distage.sbt

import izumi.distage.sbt.target.{ForeignRunReports, TaskCompleteness}
import izumi.distage.testkit.protocol.ForkProcessId

import sbt.{TestEvent, TestResult, TestsListener}
import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, TestSelector}

import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters.*

object HostForeignForkReportsTest {
  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Foreign projection checks require a new directory")
    val directory = Files.createDirectory(Paths.get(arguments.head).toAbsolutePath)
    try {
      Seq("memory", "file").foreach { mode =>
        Seq("missing", "delivered", "partial", "repeated", "process", "owner", "selector", "duration", "fingerprint", "failure", "active").foreach { scenario =>
          val child = Files.createDirectory(directory.resolve(mode + "-" + scenario))
          val store: ForeignRunReports.Store = if (mode == "file") new ForeignRunReports.FileStore(child) else new MemoryStore
          contract(mode, scenario, store)
        }
      }
    } finally {
      val entries = Files.walk(directory)
      try entries.sorted(java.util.Comparator.reverseOrder()).forEach(path => { val _ = Files.delete(path) }) finally entries.close()
    }
  }

  private def contract(mode: String, scenario: String, store: ForeignRunReports.Store): Unit = {
    val name = HostSuiteName("fixture.ForeignSuite")
    val owner = new TaskCompleteness.SuiteName(name.value)
    val fingerprint = new ForeignRunReports.SubclassSnapshot(false, "fixture.Marker", true)
    val original = Vector.tabulate(3)(index => event(name.value, fingerprint, new TestSelector("test-" + index), index.toLong, None))
    val events = original.map(ForeignRunReports.EventSnapshot.from).asJava
    store.publish(new ForeignRunReports.Report(UUID.randomUUID(), owner, owner, 101L, events))
    val repeated = Set("partial", "repeated").contains(scenario)
    if (repeated) store.publish(new ForeignRunReports.Report(UUID.randomUUID(), owner, owner, 102L, events))
    val receipt = new HostReceipt
    val received = receipt.configure(Set.empty)
    if (Set("delivered", "partial", "selector", "duration", "fingerprint", "failure", "active").contains(scenario)) {
      val altered = original.updated(0, event(name.value,
        if (scenario == "fingerprint") new ForeignRunReports.SubclassSnapshot(false, "different.Marker", true) else fingerprint,
        new TestSelector(if (scenario == "selector") "different-test" else "test-0"),
        if (scenario == "duration") 100L else 0L,
        if (scenario == "failure") Some(new IllegalStateException("unexpected failure")) else None,
      ))
      received.startGroup(name.value)
      received.testEvent(TestEvent(altered.reverse))
      if (scenario != "active") received.endGroup(name.value, TestResult.Passed)
    }
    var observed = Vector.empty[Event]
    var groups = 0
    val listener = new TestsListener {
      override def doInit(): Unit = ()
      override def doComplete(result: TestResult): Unit = ()
      override def startGroup(value: String): Unit = { require(value == name.value); groups += 1 }
      override def testEvent(value: TestEvent): Unit = observed ++= value.detail
      override def endGroup(value: String, cause: Throwable): Unit = throw cause
      override def endGroup(value: String, result: TestResult): Unit = require(value == name.value && result == TestResult.Passed)
    }
    val admissions = Vector(HostForkAdmission(ForkProcessId(if (scenario == "process") 999L else 101L), Set(if (scenario == "owner") HostSuiteName("different.Owner") else name))) ++
      (if (repeated) Vector(HostForkAdmission(ForkProcessId(102L), Set(name))) else Vector.empty)
    val projection = new HostForeignForkReports(store, Seq(received, listener), receipt)
    if (Set("process", "owner", "selector", "duration", "fingerprint", "failure", "active").contains(scenario)) {
      try { projection.cancel(admissions); throw new AssertionError("Invalid foreign projection accepted") }
      catch { case _: IllegalArgumentException => () }
      require(groups == 0 && observed.isEmpty, "Invalid foreign projection emitted callbacks")
    } else {
      projection.cancel(admissions)
      projection.cancel(admissions)
      val expectedGroups = if (scenario == "delivered") 0 else if (scenario == "repeated") 2 else 1
      require(groups == expectedGroups && observed.size == expectedGroups * 3, "Foreign groups replayed more than once or were lost")
      require(observed.grouped(3).forall(group => group.map(_.selector().asInstanceOf[TestSelector].testName()) == Vector("test-0", "test-1", "test-2")), "Foreign selectors changed")
    }
    println("HOST_FOREIGN_REPORT_CHECK_OK " + mode + " " + scenario)
  }

  private def event(name: String, marker: Fingerprint, selected: Selector, elapsed: Long, failure: Option[Throwable]): Event = new Event {
    override def fullyQualifiedName(): String = name
    override def fingerprint(): Fingerprint = marker
    override def selector(): Selector = selected
    override def status(): Status = Status.Success
    override def throwable(): OptionalThrowable = failure.fold(new OptionalThrowable)(new OptionalThrowable(_))
    override def duration(): Long = elapsed
  }

  private final class MemoryStore extends ForeignRunReports.Store {
    private var values = Vector.empty[ForeignRunReports.Report]
    override def publish(value: ForeignRunReports.Report): Unit = {
      require(!values.exists(_.token() == value.token()), "Repeated foreign report token")
      values :+= value
    }
    override def completed(): java.util.List[ForeignRunReports.Report] = values.asJava
  }
}
