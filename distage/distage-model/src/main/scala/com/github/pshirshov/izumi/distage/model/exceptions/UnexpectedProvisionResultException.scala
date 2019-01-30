package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.provisioning.ExecutableOpResult

class UnexpectedProvisionResultException(message: String, val results: Seq[ExecutableOpResult]) extends DIException(message, null)
