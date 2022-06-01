package izumi.distage.model.exceptions.interpretation

import izumi.distage.model.exceptions.DIException
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyParams

class ProxyInstantiationException(message: String, val target: Class[?], val params: ProxyParams, val op: ExecutableOp, cause: Throwable)
  extends DIException(message, cause)
