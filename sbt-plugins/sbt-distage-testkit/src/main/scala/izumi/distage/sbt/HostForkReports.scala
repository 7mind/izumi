package izumi.distage.sbt

import izumi.distage.sbt.target.TaskCompleteness
import izumi.distage.testkit.protocol.{Failure, FailurePhase, ForkProcessId, ForkRunReport, ForkRunReports, ForkSuiteOwner, ProjectedFailure}
import sbt.{MessageOnlyException, TestDefinition, TestReportListener}
import sbt.testing.{Event}

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

private[sbt] final case class HostForkSuite(process: ForkProcessId, name: HostSuiteName)
private[sbt] final case class HostForkProjection(suite: HostForkSuite, group: HostReportGroup)

private[sbt] final class HostForkReports(reports: ForkRunReports, terminals: TaskCompleteness.CompletionStore, definitions: Seq[TestDefinition], listeners: Seq[TestReportListener], receipt: HostReceipt, hostProcess: ForkProcessId) {
  private final val WaitSeconds = 60L
  private final val PollMillis = 5L
  private val suites = definitions.filter(DistageHostPolicy.isDistage).map(value => HostSuiteName(value.name) -> value).toMap
  private var finished = false

  def cancel(admissions: Vector[HostForkAdmission]): Unit = synchronized {
    if (!finished && admissions.nonEmpty) {
      require(admissions.map(_.process).distinct.size == admissions.size, "Repeated fork process admission")
      val selected = receipt.selected
      val expected = admissions.flatMap(admission => admission.suites.intersect(selected).map(name => HostForkSuite(admission.process, name))).toSet
      val processes = admissions.map(_.process).toSet + hostProcess
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WaitSeconds)
      def ready: Boolean = {
        val bound = reports.completed().flatMap(report => report.owners.map(owner => HostForkSuite(report.process, HostSuiteName(owner.name)))).toSet
        val completed = terminals.completed().asScala.map(value => HostForkSuite(ForkProcessId(value.pid()), HostSuiteName(value.suite().value()))).toSet
        expected.subsetOf(bound.intersect(completed))
      }
      while (!ready && System.nanoTime() < deadline) {
        try Thread.sleep(PollMillis)
        catch { case _: InterruptedException => val _ = Thread.interrupted() }
      }
      if (!ready) throw new MessageOnlyException("Incomplete distage fork cancellation reports: " + expected)
      val completed = reports.completed()
      require(completed.forall(report => processes.contains(report.process)), "Fork report has no admitted process")
      require(completed.map(_.outcome.run).distinct.size == completed.size, "Repeated fork run identity")
      val projections = completed.sortBy(report => (report.process != hostProcess, report.process.value, report.outcome.run.value)).flatMap { report =>
        report.owners.filter(owner => selected.contains(HostSuiteName(owner.name))).map { owner =>
          val suite = HostForkSuite(report.process, HostSuiteName(owner.name))
          require(suite.process == hostProcess || expected.contains(suite), "Fork report has no admitted suite")
          HostForkProjection(suite, HostReportGroup(suite.name, project(report, owner, suites(suite.name))))
        }
      }
      val records = terminals.completed().asScala.toVector.filter(value => selected.contains(HostSuiteName(value.suite().value())))
      require(records.forall(value => value.returnedNormally() && processes.contains(ForkProcessId(value.pid()))), "Fork cancellation has an invalid suite terminal")
      val target = records.groupMap(value => HostForkSuite(ForkProcessId(value.pid()), HostSuiteName(value.suite().value())))(HostSuiteCounts.from)
      val projected = projections.groupMap(_.suite)(_.group.identity.counts)
      require(target.keySet == projected.keySet && projected.forall { case (suite, counts) =>
        counts.groupMapReduce(identity)(_ => 1)(_ + _) == target(suite).groupMapReduce(identity)(_ => 1)(_ + _)
      }, "Fork cancellation events differ from the target terminal")
      val missing = HostReportGroup.unreported(projections, receipt, selected)(_.group, name => "Completed host group differs from every target report: " + name.value)
      require(missing.forall(_.suite.process != hostProcess), "In-process target report has no completed host group")
      missing.foreach(_.group.replay(listeners))
      finished = true
    }
  }

  private def project(report: ForkRunReport, owner: ForkSuiteOwner, definition: TestDefinition): Vector[Event] = {
    val results = report.outcome.results.filter(result => owner.id.contains(result.id.suite)).map { result =>
      HostProtocolEvent.result(definition.name, definition.fingerprint, result)
    }
    val failures = if (report.outcome.cancelled && report.outcome.failures.isEmpty) {
      Vector(Failure(FailurePhase.Transport, "TestApplicationError", "Host test run was cancelled", Vector.empty, Vector.empty, None, Vector.empty, Vector.empty))
    } else report.outcome.failures
    results ++ failures.map(failure => HostProtocolEvent.error(definition.name, definition.fingerprint, ProjectedFailure.root(failure)))
  }
}
