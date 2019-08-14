package izumi.distage.model.provisioning.strategies

import java.lang.reflect.Method

import izumi.distage.model.exceptions.NoopProvisionerImplCalled
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.{ProvisioningKeyProvider, WiringExecutor}
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

case class TraitContext(
                         index: TraitIndex
                       , context: ProvisioningKeyProvider
                       )


case class FactoryContext(
                           factoryMethodIndex: Map[Method, RuntimeDIUniverse.Wiring.Factory.FactoryMethod]
                         , dependencyMethodIndex: TraitIndex
                         , narrowedContext: ProvisioningKeyProvider
                         , executor: WiringExecutor
                         , op: WiringOp.InstantiateFactory
                         )

case class CycleContext(deferredKey: RuntimeDIUniverse.DIKey)

case class ProxyContext(runtimeClass: Class[_], op: ExecutableOp, params: ProxyParams)

trait ProxyProvider {
  def makeFactoryProxy(factoryContext: FactoryContext, proxyContext: ProxyContext): AnyRef

  def makeTraitProxy(traitContext: TraitContext, proxyContext: ProxyContext): AnyRef

  def makeCycleProxy(cycleContext: CycleContext, proxyContext: ProxyContext): DeferredInit
}

class ProxyProviderFailingImpl extends ProxyProvider {
  override def makeFactoryProxy(factoryContext: FactoryContext, proxyContext: ProxyContext): AnyRef = {
    Quirks.discard(factoryContext)
    throw new NoopProvisionerImplCalled(s"ProxyProviderFailingImpl can't create factory proxies, failed op: ${proxyContext.op}", this)
  }

  override def makeTraitProxy(traitContext: TraitContext, proxyContext: ProxyContext): AnyRef = {
    Quirks.discard(traitContext)
    throw new NoopProvisionerImplCalled(s"ProxyProviderFailingImpl can't create trait proxies, failed op: ${proxyContext.op}", this)
  }

  override def makeCycleProxy(cycleContext: CycleContext, proxyContext: ProxyContext): DeferredInit = {
    Quirks.discard(cycleContext)
    throw new NoopProvisionerImplCalled(s"ProxyProviderFailingImpl can't create cycle-breaking proxies, failed op: ${proxyContext.op}", this)
  }
}
