package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class MissingProxyAdapterException(message: String, val key: RuntimeDIUniverse.DIKey, val op: ProxyOp) extends DIException(message, null)
