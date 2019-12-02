package izumi.distage.model.provisioning.strategies

import izumi.distage.model.exceptions.NoopProvisionerImplCalled
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.provisioning.strategies.ProxyProvider.{DeferredInit, ProxyContext}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

trait ProxyProvider {
  def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit
}

object ProxyProvider {
  class ProxyProviderFailingImpl extends ProxyProvider {
    override def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit = {
      throw new NoopProvisionerImplCalled(s"ProxyProviderFailingImpl can't create cycle-breaking proxies, failed op: ${proxyContext.op}", this)
    }
  }

  final case class ProxyContext(runtimeClass: Class[_], op: ExecutableOp, params: ProxyParams)

  sealed trait ProxyParams
  object ProxyParams {
    final case object Empty extends ProxyParams
    final case class Params(types: Array[Class[_]], values: Array[AnyRef]) extends ProxyParams {
      override def toString: String = s"Params(${types.mkString("[", ",", "]")}, ${values.mkString("[", ",", "]")})"
    }
  }

  final case class DeferredInit(dispatcher: ProxyDispatcher, proxy: AnyRef)
}
