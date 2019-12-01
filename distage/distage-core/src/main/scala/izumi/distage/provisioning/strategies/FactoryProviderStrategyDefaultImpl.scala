package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.InvalidPlanException
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.provisioning.strategies.{FactoryExecutor, FactoryProviderStrategy}
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class FactoryProviderStrategyDefaultImpl extends FactoryProviderStrategy  {
  def callFactoryProvider(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.CallFactoryProvider): Seq[NewObjectOp.NewInstance] = {

    val args: Seq[TypedRef[_]] = op.wiring.providerArguments.map {
      key =>
        context.fetchKey(key.key, key.isByName) match {
          case Some(dep) =>
            TypedRef(dep, key.key.tpe)
          case _ if key.key == DIKey.get[FactoryExecutor] =>
            TypedRef(mkExecutor(context, executor, op.wiring.factoryIndex, op))
          case _ =>
            throw new InvalidPlanException("The impossible happened! Tried to instantiate class," +
                s" but the dependency has not been initialized: Class: $op.target, dependency: $key")
        }
    }

    val instance = op.wiring.provider.unsafeApply(args: _*)
    Seq(NewObjectOp.NewInstance(op.target, instance))
  }

  private def mkExecutor(context: ProvisioningKeyProvider, executor: WiringExecutor, factoryIndex: Map[Int, Wiring.FactoryFunction.FactoryMethod], op: WiringOp.CallFactoryProvider): FactoryExecutor =
    (idx, args) => {
      val Wiring.FactoryFunction.FactoryMethod(_, wireWith, methodArguments) = factoryIndex(idx)

      val productDeps = wireWith.requiredKeys
      val narrowedContext = context.narrow(productDeps)

      val argsWithKeys = methodArguments.zip(args).toMap

      val extendedContext = narrowedContext.extend(argsWithKeys)

      executor.execute(extendedContext, mkExecutableOp(op.target, wireWith, op.origin))
    }

  private[this] def mkExecutableOp(key: RuntimeDIUniverse.DIKey, w: RuntimeDIUniverse.Wiring.SingletonWiring.Function, origin: OperationOrigin): WiringOp = {
    val target = RuntimeDIUniverse.DIKey.ProxyElementKey(key, w.instanceType)
    WiringOp.CallProvider(target, w, origin)
  }
}

