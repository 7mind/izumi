package izumi.distage.provisioning.strategies

import izumi.distage.commons.TraitInitTool
import izumi.distage.model.exceptions.{NoRuntimeClassException, NoopProvisionerImplCalled}
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}
import izumi.distage.model.reflection.universe.MirrorProvider
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.reflection.ReflectionUtil

class FactoryStrategyDefaultImpl
(
  proxyProvider: ProxyProvider,
  mirror: MirrorProvider,
  traitInit: TraitInitTool,
) extends FactoryStrategy {
  def makeFactory(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.InstantiateFactory): Seq[NewObjectOp] = {
    // at this point we definitely have all the dependencies instantiated
    val narrowedContext = context.narrow(op.wiring.requiredKeys)

    val factoryMethodIndex = makeFactoryIndex(op)
    val traitIndex = traitInit.traitIndex(op.wiring.factoryType, op.wiring.fieldDependencies)

    val instanceType = op.wiring.factoryType
    val runtimeClass = mirror.runtimeClass(instanceType).getOrElse(throw new NoRuntimeClassException(op.target))

    val factoryContext = FactoryContext(
      factoryMethodIndex
      , traitIndex
      , narrowedContext
      , executor
      , op
    )

    val proxyInstance = proxyProvider.makeFactoryProxy(factoryContext, ProxyContext(runtimeClass, op, ProxyParams.Empty))
    traitInit.initTrait(instanceType, runtimeClass, proxyInstance)
    Seq(NewObjectOp.NewInstance(op.target, proxyInstance))
  }

  private def makeFactoryIndex(op: WiringOp.InstantiateFactory) = {
    op.wiring.factoryMethods.map {
      wiring =>
        op.wiring.factoryType.use(t => ReflectionUtil.toJavaMethod(t, wiring.factoryMethod.underlying))-> wiring

    }.toMap
  }
}


class FactoryStrategyFailingImpl extends FactoryStrategy {
  override def makeFactory(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.InstantiateFactory): Seq[NewObjectOp] = {
    Quirks.discard(context, executor)
    throw new NoopProvisionerImplCalled(s"FactoryStrategyFailingImpl does not support proxies, failed op: $op", this)
  }
}
