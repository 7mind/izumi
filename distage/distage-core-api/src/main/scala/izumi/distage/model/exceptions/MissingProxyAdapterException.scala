package izumi.distage.model.exceptions

import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.reflection.universe.RuntimeDIUniverse

class MissingProxyAdapterException(message: String, val key: RuntimeDIUniverse.DIKey, val op: ProxyOp) extends DIException(message)
