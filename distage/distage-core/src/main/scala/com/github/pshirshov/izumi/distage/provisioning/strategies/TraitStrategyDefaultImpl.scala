package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.commons.TraitTools
import com.github.pshirshov.izumi.distage.model.exceptions.NoopProvisionerImplCalled
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.MirrorProvider
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

class TraitStrategyDefaultImpl(proxyProvider: ProxyProvider, mirror: MirrorProvider) extends TraitStrategy {
  def makeTrait(context: ProvisioningKeyProvider, op: WiringOp.InstantiateTrait): Seq[OpResult] = {
    val traitDeps = context.narrow(op.wiring.requiredKeys)

    val wiredMethodIndex = TraitTools.traitIndex(op.wiring.instanceType, op.wiring.associations)

    val instanceType = op.wiring.instanceType
    val runtimeClass = mirror.runtimeClass(instanceType)

    val traitProxy = proxyProvider.makeTraitProxy(TraitContext(wiredMethodIndex, traitDeps), ProxyContext(runtimeClass, op, ProxyParams.Empty))
    TraitTools.initTrait(instanceType, runtimeClass, traitProxy)
    Seq(OpResult.NewInstance(op.target, traitProxy))
  }

}

class TraitStrategyFailingImpl extends TraitStrategy {
  override def makeTrait(context: ProvisioningKeyProvider, op: WiringOp.InstantiateTrait): Seq[OpResult] = {
    Quirks.discard(context)
    throw new NoopProvisionerImplCalled(s"TraitStrategyFailingImpl does not support proxies, failed op: $op", this)
  }
}
