package izumi.distage.model.exceptions

import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.reflection.DIKey

class MissingProxyAdapterException(message: String, val key: DIKey, val op: ProxyOp) extends DIException(message)
