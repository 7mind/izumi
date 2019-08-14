package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.commons.TraitInitTool
import com.github.pshirshov.izumi.distage.model.exceptions.{NoRuntimeClassException, NoopProvisionerImplCalled}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.MirrorProvider
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

class TraitStrategyDefaultImpl
(
  proxyProvider: ProxyProvider,
  mirror: MirrorProvider,
  traitInit: TraitInitTool,
) extends TraitStrategy {
  def makeTrait(context: ProvisioningKeyProvider, op: WiringOp.InstantiateTrait): Seq[NewObjectOp.NewInstance] = {
    val traitDeps = context.narrow(op.wiring.requiredKeys)

    val wiredMethodIndex = traitInit.traitIndex(op.wiring.instanceType, op.wiring.associations)

    val instanceType = op.wiring.instanceType
    val runtimeClass = mirror.runtimeClass(instanceType).getOrElse(throw new NoRuntimeClassException(op.target))

    val traitProxy = proxyProvider.makeTraitProxy(TraitContext(wiredMethodIndex, traitDeps), ProxyContext(runtimeClass, op, ProxyParams.Empty))
    traitInit.initTrait(instanceType, runtimeClass, traitProxy)
    Seq(NewObjectOp.NewInstance(op.target, traitProxy))
  }

}

class TraitStrategyFailingImpl extends TraitStrategy {
  override def makeTrait(context: ProvisioningKeyProvider, op: WiringOp.InstantiateTrait): Seq[NewObjectOp.NewInstance] = {
    Quirks.discard(context)
    throw new NoopProvisionerImplCalled(s"TraitStrategyFailingImpl does not support proxies, failed op: $op", this)
  }
}
