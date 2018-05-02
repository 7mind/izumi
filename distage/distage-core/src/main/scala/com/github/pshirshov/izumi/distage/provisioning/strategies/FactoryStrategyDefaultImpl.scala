package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.commons.TraitTools
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.FactoryStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.cglib.{CgLibFactoryMethodInterceptor, CglibTools, ProxyParams}
import com.github.pshirshov.izumi.fundamentals.reflection.ReflectionUtil


class FactoryStrategyDefaultImpl extends FactoryStrategy {
  def makeFactory(context: ProvisioningContext, executor: OperationExecutor, op: WiringOp.InstantiateFactory): Seq[OpResult] = {
    // at this point we definitely have all the dependencies instantiated

    val allRequiredKeys = op.wiring.associations.map(_.wireWith).toSet
    val narrowedContext = context.narrow(allRequiredKeys)

    val factoryMethodIndex = makeFactoryIndex(op)
    val traitIndex = TraitTools.traitIndex(op.wiring.factoryType, op.wiring.fieldDependencies)

    val instanceType = op.wiring.factoryType
    val runtimeClass = RuntimeDIUniverse.mirror.runtimeClass(instanceType.tpe)
    val dispatcher = new CgLibFactoryMethodInterceptor(
      factoryMethodIndex
      , traitIndex
      , narrowedContext
      , executor
      , op
    )

    CglibTools.mkDynamic(dispatcher, runtimeClass, op, ProxyParams.Empty) {
      instance =>
        TraitTools.initTrait(instanceType, runtimeClass, instance)
        Seq(OpResult.NewInstance(op.target, instance))
    }
  }

  private def makeFactoryIndex(op: WiringOp.InstantiateFactory) = {
    op.wiring.factoryMethods.map {
      wiring =>
        ReflectionUtil.toJavaMethod(op.wiring.factoryType.tpe, wiring.factoryMethod) -> wiring
    }.toMap
  }
}


