package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.provisioning.NewObjectOp

class UnexpectedProvisionResultException(message: String, val results: Seq[NewObjectOp]) extends DIException(message, null)
