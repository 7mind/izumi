package izumi.distage.sbt

import izumi.distage.sbt.target.TaskCompleteness
import izumi.distage.testkit.protocol.{Failure, FailurePhase, ForkRunReport, ForkRunReports, ForkSuiteOwner, ProjectedFailure, TestStatus}
import sbt.{MessageOnlyException, SuiteResult, TestDefinition, TestEvent, TestReportListener}
import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, SuiteSelector, TestSelector}

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

private[sbt] final class HostForkReports(reports: ForkRunReports, terminals: TaskCompleteness.CompletionStore, definitions: Seq[TestDefinition], listeners: Seq[TestReportListener], receipt: HostReceipt) {
  private final val WaitSeconds = 60L
  private final val PollMillis = 5L
  private val suites = definitions.filter(DistageHostPolicy.isDistage).map(value => HostSuiteName(value.name) -> value).toMap
  private var finished = false

  def cancel(names: Set[HostSuiteName]): Unit = synchronized {
    if (!finished && names.nonEmpty) {
      val expected = names.intersect(receipt.selected)
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WaitSeconds)
      def ready: Boolean = {
        val bound = reports.completed().flatMap(_.owners).map(owner => HostSuiteName(owner.name)).toSet
        val completed = terminals.completed().asScala.map(value => HostSuiteName(value.suite().value())).toSet
        expected.subsetOf(bound.intersect(completed))
      }
      while (!ready && System.nanoTime() < deadline) {
        try Thread.sleep(PollMillis)
        catch { case _: InterruptedException => val _ = Thread.interrupted() }
      }
      if (!ready) throw new MessageOnlyException("Incomplete distage fork cancellation reports: " + expected)
      val completed = reports.completed()
      val owners = completed.flatMap(_.owners).filter(owner => expected.contains(HostSuiteName(owner.name)))
      require(owners.map(_.name).distinct.size == owners.size, "Fork cancellation has ambiguous repeated suite groups")
      completed.foreach { report =>
        report.owners.filter(owner => expected.contains(HostSuiteName(owner.name))).foreach { owner =>
          val name = HostSuiteName(owner.name)
          if (receipt.unreported(name)) {
            val events = project(report, owner, suites(name))
            val records = terminals.completed().asScala.filter(_.suite().value() == owner.name).toVector
            require(records.size == 1, "Fork cancellation has no unique suite terminal")
            val target = records.head.counts()
            val received = SuiteResult(events)
            require(Vector(received.passedCount, received.failureCount, received.errorCount, received.skippedCount, received.ignoredCount, received.canceledCount, received.pendingCount) ==
              Vector(target.success(), target.failure(), target.error(), target.skipped(), target.ignored(), target.canceled(), target.pending()), "Fork cancellation events differ from the target terminal")
            listeners.foreach(_.startGroup(owner.name))
            listeners.foreach(_.testEvent(TestEvent(events)))
            listeners.foreach(_.endGroup(owner.name, received.result))
          }
        }
      }
      finished = true
    }
  }

  private def project(report: ForkRunReport, owner: ForkSuiteOwner, definition: TestDefinition): Vector[Event] = {
    val results = report.outcome.results.filter(result => owner.id.contains(result.id.suite)).map { result =>
      val status = result.status match {
        case TestStatus.Succeeded => Status.Success
        case TestStatus.Failed => Status.Failure
        case TestStatus.Cancelled => Status.Canceled
        case TestStatus.Skipped => Status.Skipped
      }
      event(definition, new TestSelector(result.id.path.mkString(" ")), status, result.failure, TimeUnit.NANOSECONDS.toMillis(result.durationNanos))
    }
    val failures = if (report.outcome.cancelled && report.outcome.failures.isEmpty) {
      Vector(Failure(FailurePhase.Transport, "TestApplicationError", "Host test run was cancelled", Vector.empty, Vector.empty, None, Vector.empty, Vector.empty))
    } else report.outcome.failures
    results ++ failures.map(failure => event(definition, new SuiteSelector, Status.Error, Some(failure), 0L))
  }

  private def event(definition: TestDefinition, selectorValue: Selector, statusValue: Status, failure: Option[Failure], durationValue: Long): Event = new Event {
    private val cause = failure.fold(new OptionalThrowable)(value => new OptionalThrowable(ProjectedFailure.root(value)))
    override def fullyQualifiedName(): String = definition.name
    override def fingerprint(): Fingerprint = definition.fingerprint
    override def selector(): Selector = selectorValue
    override def status(): Status = statusValue
    override def throwable(): OptionalThrowable = cause
    override def duration(): Long = durationValue
  }
}
