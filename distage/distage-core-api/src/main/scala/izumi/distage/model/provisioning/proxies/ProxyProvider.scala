package izumi.distage.model.provisioning.proxies

import izumi.distage.model.exceptions.interpretation.ProxyProviderFailingImplCalledException
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext}
import izumi.distage.model.reflection.DIKey

trait ProxyProvider {
  def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit
}

object ProxyProvider {
  object ProxyProviderFailingImpl extends ProxyProvider {
    override def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit = {
      throw new ProxyProviderFailingImplCalledException(s"ProxyProviderFailingImpl can't create cycle-breaking proxies, failed op: ${proxyContext.op}", this)
    }
  }

  final case class ProxyContext(runtimeClass: Class[?], op: ExecutableOp, params: ProxyParams)

  sealed trait ProxyParams
  object ProxyParams {
    final case object Empty extends ProxyParams
    final case class Params(types: Array[Class[?]], values: Array[Any]) extends ProxyParams {
      override def toString: String = s"Params(${types.mkString("[", ",", "]")}, ${values.mkString("[", ",", "]")})"
    }
  }

  final case class DeferredInit(dispatcher: ProxyDispatcher, proxy: AnyRef)
}
