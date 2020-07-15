package izumi.distage.framework.model.exceptions

import izumi.distage.model.exceptions.DIException
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.strings.IzString._

class IntegrationCheckException(val failures: Seq[ResourceCheck.Failure])
  extends DIException(
    s"""Integration check failed, failures were: ${failures.niceList()}""".stripMargin
  )
