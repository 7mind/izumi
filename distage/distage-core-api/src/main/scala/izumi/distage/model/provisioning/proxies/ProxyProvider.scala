package izumi.distage.model.provisioning.proxies

import izumi.distage.model.exceptions.interpretation.ProxyProviderFailingImplCalledException
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext}
import izumi.distage.model.reflection.DIKey

trait ProxyProvider {
  def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit
}

object ProxyProvider {
  class ProxyProviderFailingImpl(msg: ProxyContext => String) extends ProxyProvider {
    override def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit = {
      throw new ProxyProviderFailingImplCalledException(msg(proxyContext), this)
    }
  }
  object ProxyProviderFailingImpl
    extends ProxyProviderFailingImpl({
      proxyContext => s"ProxyProviderFailingImpl used: creation of cycle-breaking proxies is disabled, failed op: ${proxyContext.op}"
    })

  final case class ProxyContext(runtimeClass: Class[?], op: ExecutableOp, params: ProxyParams)

  sealed trait ProxyParams
  object ProxyParams {
    case object Empty extends ProxyParams
    final case class Params(types: Array[Class[?]], values: Array[Any]) extends ProxyParams {
      override def toString: String = s"Params(${types.mkString("[", ",", "]")}, ${values.mkString("[", ",", "]")})"
    }
  }

  final case class DeferredInit(dispatcher: ProxyDispatcher, proxy: AnyRef)
}
