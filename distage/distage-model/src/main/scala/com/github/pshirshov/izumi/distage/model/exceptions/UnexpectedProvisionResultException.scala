package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.provisioning.ContextAssignment

class UnexpectedProvisionResultException(message: String, val results: Seq[ContextAssignment]) extends DIException(message, null)
