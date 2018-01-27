package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.commons.TraitTools
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.provisioning.cglib.{CgLibFactoryMethodInterceptor, CglibTools}
import com.github.pshirshov.izumi.distage.provisioning.{OpResult, OperationExecutor, ProvisioningContext}
import com.github.pshirshov.izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.runtime._


class FactoryStrategyDefaultImpl extends FactoryStrategy {
  def makeFactory(context: ProvisioningContext, executor: OperationExecutor, f: WiringOp.InstantiateFactory): Seq[OpResult] = {
    // at this point we definitely have all the dependencies instantiated

    val allRequiredKeys = f.wiring.associations.map(_.wireWith).toSet
    val narrowedContext = context.narrow(allRequiredKeys)

    val factoryMethodIndex = makeFactoryIndex(f)
    val depMethodIndex = TraitStrategyDefaultImpl.traitIndex(f.wiring.factoryType, f.wiring.dependencies)

    val instanceType = f.wiring.factoryType
    val runtimeClass = currentMirror.runtimeClass(instanceType.tpe)
    val dispatcher = new CgLibFactoryMethodInterceptor(
      factoryMethodIndex
      , depMethodIndex
      , narrowedContext
      , executor
      , f
    )

    CglibTools.mkdynamic(dispatcher, runtimeClass, f) {
      instance =>
        TraitTools.initTrait(instanceType, runtimeClass, instance)
        Seq(OpResult.NewInstance(f.target, instance))
    }
  }

  private def makeFactoryIndex(f: WiringOp.InstantiateFactory) = {
    f.wiring.wirings.map {
      wiring =>
        ReflectionUtil.toJavaMethod(f.wiring.factoryType, wiring.factoryMethod) -> wiring
    }.toMap
  }
}


object FactoryStrategyDefaultImpl {
  final val instance = new FactoryStrategyDefaultImpl()
}
