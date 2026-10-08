package izumi.distage.sbt

import sbt.{SuiteResult, TestEvent, TestReportListener}
import sbt.testing.Event

private[sbt] final case class HostReportGroup(name: HostSuiteName, events: Vector[Event]) {
  val result: SuiteResult = SuiteResult(events)
  val identity: HostGroupIdentity = HostGroupIdentity(HostSuiteCounts.from(result), events.map(HostEventIdentity.from))

  def replay(listeners: Seq[TestReportListener]): Unit = {
    listeners.foreach(_.startGroup(name.value))
    listeners.foreach(_.testEvent(TestEvent(events)))
    listeners.foreach(_.endGroup(name.value, result.result))
  }
}

private[sbt] object HostReportGroup {
  def unreported[A](reports: Vector[A], receipt: HostReceipt, names: Set[HostSuiteName])(group: A => HostReportGroup, mismatch: HostSuiteName => String): Vector[A] = {
    var missing = reports
    receipt.completedGroups(names).foreach { case (name, groups) =>
      groups.foreach { completed =>
        val index = missing.indexWhere { value =>
          val projected = group(value)
          projected.name == name && projected.identity.matches(completed)
        }
        require(index >= 0, mismatch(name))
        missing = missing.patch(index, Nil, 1)
      }
    }
    missing
  }
}
