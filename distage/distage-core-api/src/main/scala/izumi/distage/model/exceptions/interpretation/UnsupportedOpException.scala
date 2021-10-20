package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.plan.ExecutableOp

@deprecated("Needs to be removed", "20/10/2021")
class UnsupportedOpException(message: String, val op: ExecutableOp) extends DIException(message)
