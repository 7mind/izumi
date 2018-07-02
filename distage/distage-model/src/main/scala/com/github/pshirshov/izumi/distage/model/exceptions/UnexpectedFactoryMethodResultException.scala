package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.provisioning.OpResult

class UnexpectedFactoryMethodResultException(message: String, val results: Seq[OpResult]) extends DIException(message, null)
