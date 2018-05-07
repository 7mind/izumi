package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.commons.TraitTools
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse


class TraitStrategyDefaultImpl(proxyProvider: ProxyProvider) extends TraitStrategy {
  def makeTrait(context: ProvisioningContext, op: WiringOp.InstantiateTrait): Seq[OpResult] = {
    val traitDeps = context.narrow(op.wiring.associations.map(_.wireWith).toSet)

    val wiredMethodIndex = TraitTools.traitIndex(op.wiring.instanceType, op.wiring.associations)

    val instanceType = op.wiring.instanceType
    val runtimeClass = RuntimeDIUniverse.mirror.runtimeClass(instanceType.tpe)

    val traitProxy = proxyProvider.makeTraitProxy(TraitContext(wiredMethodIndex, traitDeps), ProxyContext(runtimeClass, op, ProxyParams.Empty))
    TraitTools.initTrait(instanceType, runtimeClass, traitProxy)
    Seq(OpResult.NewInstance(op.target, traitProxy))
  }

}

