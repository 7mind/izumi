package com.github.pshirshov.izumi.distage.provisioning.strategies.cglib.exceptions

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyParams

class CgLibInstantiationOpException(message: String, val target: Class[_], val params: ProxyParams, val op: ExecutableOp, cause: Throwable) extends DIException(message, cause)
