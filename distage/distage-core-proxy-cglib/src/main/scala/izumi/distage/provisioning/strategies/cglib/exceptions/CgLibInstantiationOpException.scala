package izumi.distage.provisioning.strategies.cglib.exceptions

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyParams

class CgLibInstantiationOpException(message: String, val target: Class[_], val params: ProxyParams, val op: ExecutableOp, cause: Throwable)
  extends DIException(message, cause)
