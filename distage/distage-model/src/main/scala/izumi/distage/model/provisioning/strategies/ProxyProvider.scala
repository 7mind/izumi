package izumi.distage.model.provisioning.strategies

import izumi.distage.model.exceptions.NoopProvisionerImplCalled
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.fundamentals.platform.language.Quirks

case class DeferredInit(dispatcher: ProxyDispatcher, proxy: AnyRef)

sealed trait ProxyParams
object ProxyParams {
  final case object Empty extends ProxyParams

  final case class Params(types: Array[Class[_]], values: Array[AnyRef]) extends ProxyParams {
    override def toString: String = s"Params(${types.mkString("[", ",", "]")}, ${values.mkString("[", ",", "]")})"
  }
}

case class CycleContext(deferredKey: RuntimeDIUniverse.DIKey)

case class ProxyContext(runtimeClass: Class[_], op: ExecutableOp, params: ProxyParams)

trait ProxyProvider {
  def makeCycleProxy(cycleContext: CycleContext, proxyContext: ProxyContext): DeferredInit
}

class ProxyProviderFailingImpl extends ProxyProvider {
  override def makeCycleProxy(cycleContext: CycleContext, proxyContext: ProxyContext): DeferredInit = {
    Quirks.discard(cycleContext)
    throw new NoopProvisionerImplCalled(s"ProxyProviderFailingImpl can't create cycle-breaking proxies, failed op: ${proxyContext.op}", this)
  }
}
