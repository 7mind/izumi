package izumi.distage.sbt

import izumi.distage.sbt.target.ForeignRunReports

import sbt.{SuiteResult, TestEvent, TestReportListener}
import sbt.testing.Event

import scala.jdk.CollectionConverters.*

private[sbt] final case class HostForeignProjection(name: HostSuiteName, events: Vector[Event]) {
  val result: SuiteResult = SuiteResult(events)
  val identity: HostGroupIdentity = HostGroupIdentity(HostSuiteCounts.from(result), events.map(HostEventIdentity.from))
}

private[sbt] final class HostForeignForkReports(reports: ForeignRunReports.Store, listeners: Seq[TestReportListener], receipt: HostReceipt) {
  private var finished = false

  def cancel(admissions: Vector[HostForkAdmission]): Unit = synchronized {
    if (!finished) {
      val admitted = admissions.map(value => value.process.value -> value.suites).toMap
      require(admitted.size == admissions.size, "Repeated foreign fork process admission")
      val completed = reports.completed().asScala.toVector
      require(completed.map(_.token()).distinct.size == completed.size, "Repeated foreign target report token")
      require(completed.forall(value => admitted.get(value.pid()).exists(_.contains(HostSuiteName(value.owner().value()))) && !receipt.owns(HostSuiteName(value.owner().value()))), "Foreign target report has no admitted suite")
      var missing = completed.sortBy(value => (value.pid(), value.token().toString)).map(value => HostForeignProjection(HostSuiteName(value.group().value()), value.events().asScala.toVector))
      receipt.completedGroups(missing.map(_.name).toSet).foreach { case (name, groups) =>
        groups.foreach { group =>
          val index = missing.indexWhere(value => value.name == name && value.identity.matches(group))
          require(index >= 0, "Completed foreign host group differs from every target report: " + name.value)
          missing = missing.patch(index, Nil, 1)
        }
      }
      missing.foreach { projection =>
        listeners.foreach(_.startGroup(projection.name.value))
        listeners.foreach(_.testEvent(TestEvent(projection.events)))
        listeners.foreach(_.endGroup(projection.name.value, projection.result.result))
      }
      finished = true
    }
  }
}
