package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.testkit.model.SuiteId
import org.scalatest.StatefulStatus
import org.scalatest.events.{Event, Ordinal}

trait SuiteHandlerById {
  def doReportEvent(suiteId: SuiteId)(f: Ordinal => Event): Unit
  def doSetStatus(suiteId: SuiteId)(f: StatefulStatus => Unit): Unit
}
