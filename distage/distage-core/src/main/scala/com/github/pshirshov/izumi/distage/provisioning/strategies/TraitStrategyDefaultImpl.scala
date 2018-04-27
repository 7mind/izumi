package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.commons.TraitTools
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.TraitStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.provisioning.cglib.{CgLibTraitMethodInterceptor, CglibTools, ProxyParams}


class TraitStrategyDefaultImpl extends TraitStrategy {
  def makeTrait(context: ProvisioningContext, op: WiringOp.InstantiateTrait): Seq[OpResult] = {
    val traitDeps = context.narrow(op.wiring.associations.map(_.wireWith).toSet)

    val wiredMethodIndex = TraitTools.traitIndex(op.wiring.instanceType, op.wiring.associations)

    val instanceType = op.wiring.instanceType
    val runtimeClass = RuntimeUniverse.mirror.runtimeClass(instanceType.tpe)
    val dispatcher = new CgLibTraitMethodInterceptor(wiredMethodIndex, traitDeps)

    CglibTools.mkDynamic(dispatcher, runtimeClass, op, ProxyParams.Empty) {
      instance =>
        TraitTools.initTrait(instanceType, runtimeClass, instance)
        Seq(OpResult.NewInstance(op.target, instance))
    }
  }

}

