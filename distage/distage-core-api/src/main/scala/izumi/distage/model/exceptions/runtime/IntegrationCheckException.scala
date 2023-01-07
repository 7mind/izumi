package izumi.distage.model.exceptions.runtime

import izumi.distage.model.exceptions.DIException
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.strings.IzString.*

class IntegrationCheckException(val failures: NonEmptyList[ResourceCheck.Failure])
  extends DIException(
    s"""Integration check failed, failures were: ${failures.toList.niceList()}""".stripMargin
  ) {
  def this(failure: ResourceCheck.Failure) = this(NonEmptyList(failure))
}
