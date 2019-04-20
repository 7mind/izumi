package com.github.pshirshov.izumi.distage.roles.launcher.exceptions

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck

@deprecated("migrate to new api", "2019-04-20")
class IntegrationCheckException(message: String, val failures: Seq[ResourceCheck.Failure]) extends DIException(message, null)

@deprecated("migrate to new api", "2019-04-20")
class LifecycleException(message: String, cause: Option[Throwable] = None) extends DIException(message, cause.orNull)
