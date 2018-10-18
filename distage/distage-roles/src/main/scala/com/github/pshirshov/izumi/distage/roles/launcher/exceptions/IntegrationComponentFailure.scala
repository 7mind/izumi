package com.github.pshirshov.izumi.distage.roles.launcher.exceptions

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.roles.roles.ResourceCheck

class IntegrationComponentFailure(message: String, val failure: ResourceCheck.Failure, val cause: Option[Throwable]) extends DIException(message, cause.orNull)
