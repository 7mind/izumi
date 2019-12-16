package izumi.distage.model.exceptions

import izumi.distage.model.plan.ExecutableOp

class UnsupportedOpException(message: String, val op: ExecutableOp) extends DIException(message)
