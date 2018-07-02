package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.commons.TraitTools
import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, NoopProvisionerImplCalled}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.reflection.ReflectionUtil


class FactoryStrategyDefaultImpl(proxyProvider: ProxyProvider) extends FactoryStrategy {
  def makeFactory(context: ProvisioningKeyProvider, executor: OperationExecutor, op: WiringOp.InstantiateFactory): Seq[OpResult] = {
    // at this point we definitely have all the dependencies instantiated

    val allRequiredKeys = op.wiring.associations.map(_.wireWith).toSet
    val narrowedContext = context.narrow(allRequiredKeys)

    val factoryMethodIndex = makeFactoryIndex(op)
    val traitIndex = TraitTools.traitIndex(op.wiring.factoryType, op.wiring.fieldDependencies)

    val instanceType = op.wiring.factoryType
    val runtimeClass = RuntimeDIUniverse.mirror.runtimeClass(instanceType.tpe)

    val factoryContext = FactoryContext(
      factoryMethodIndex
      , traitIndex
      , narrowedContext
      , executor
      , op
    )

    val proxyInstance = proxyProvider.makeFactoryProxy(factoryContext, ProxyContext(runtimeClass, op, ProxyParams.Empty))
    TraitTools.initTrait(instanceType, runtimeClass, proxyInstance)
    Seq(OpResult.NewInstance(op.target, proxyInstance))
  }

  private def makeFactoryIndex(op: WiringOp.InstantiateFactory) = {
    op.wiring.factoryMethods.map {
      wiring =>
        ReflectionUtil.toJavaMethod(op.wiring.factoryType.tpe, wiring.factoryMethod.underlying) -> wiring
    }.toMap
  }
}


class FactoryStrategyFailingImpl extends FactoryStrategy {
  override def makeFactory(context: ProvisioningKeyProvider, executor: OperationExecutor, op: WiringOp.InstantiateFactory): Seq[OpResult] = {
    Quirks.discard(context, executor)
    throw new NoopProvisionerImplCalled(s"FactoryStrategyFailingImpl does not support proxies, failed op: $op", this)
  }
}
