package izumi.distage.model.exceptions.runtime

import izumi.distage.model.exceptions.DIException
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.strings.IzString.*

class IntegrationCheckException(val failures: NEList[ResourceCheck.Failure], captureStackTrace: Boolean)
  extends DIException(
    s"""Integration check failed, failures were: ${failures.toList.niceList()}""".stripMargin,
    null,
    captureStackTrace,
  ) {
  def this(failures: NEList[ResourceCheck.Failure]) = this(failures, true)
  def this(failure: ResourceCheck.Failure) = this(NEList(failure))
}
