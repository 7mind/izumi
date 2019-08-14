package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp

class UnsupportedOpException(message: String, val op: ExecutableOp) extends DIException(message, null)
