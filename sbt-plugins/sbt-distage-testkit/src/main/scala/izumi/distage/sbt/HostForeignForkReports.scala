package izumi.distage.sbt

import izumi.distage.sbt.target.ForeignRunReports

import sbt.TestReportListener

import scala.jdk.CollectionConverters.*

private[sbt] final class HostForeignForkReports(reports: ForeignRunReports.Store, listeners: Seq[TestReportListener], receipt: HostReceipt) {
  private var finished = false

  def cancel(admissions: Vector[HostForkAdmission]): Unit = synchronized {
    if (!finished) {
      val admitted = admissions.map(value => value.process.value -> value.suites).toMap
      require(admitted.size == admissions.size, "Repeated foreign fork process admission")
      val completed = reports.completed().asScala.toVector
      require(completed.map(_.token()).distinct.size == completed.size, "Repeated foreign target report token")
      require(completed.forall(value => admitted.get(value.pid()).exists(_.contains(HostSuiteName(value.owner().value()))) && !receipt.owns(HostSuiteName(value.owner().value()))), "Foreign target report has no admitted suite")
      val projections = completed.sortBy(value => (value.pid(), value.token().toString)).map { value =>
        HostReportGroup(HostSuiteName(value.group().value()), value.events().asScala.toVector)
      }
      val missing = HostReportGroup.unreported(projections, receipt, projections.map(_.name).toSet)(
        identity, name => "Completed foreign host group differs from every target report: " + name.value,
      )
      missing.foreach(_.replay(listeners))
      finished = true
    }
  }
}
