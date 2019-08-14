package izumi.distage.provisioning.strategies

import izumi.distage.model.LoggerHook
import izumi.distage.model.exceptions.InvalidPlanException
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.strategies.{FactoryExecutor, FactoryProviderStrategy}
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider, WiringExecutor}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.distage.provisioning.FactoryTools

class FactoryProviderStrategyDefaultImpl
(
  loggerHook: LoggerHook
) extends FactoryProviderStrategy  {
  def callFactoryProvider(context: ProvisioningKeyProvider, executor: WiringExecutor, op: WiringOp.CallFactoryProvider): Seq[NewObjectOp.NewInstance] = {

    val args: Seq[TypedRef[_]] = op.wiring.providerArguments.map {
      key =>
        context.fetchKey(key.wireWith, key.isByName) match {
          case Some(dep) =>
            TypedRef(dep, key.wireWith.tpe)
          case _ if key.wireWith == DIKey.get[FactoryExecutor] =>
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
      loggerHook.log(s"FactoryExecutor: Start! Looking up method index $idx in $factoryIndex")

      val method@Wiring.FactoryFunction.FactoryMethod(_, wireWith, methodArguments) = factoryIndex(idx)

      loggerHook.log(s"FactoryExecutor: Executing method $method with ${args.toList} in context $context")

      val productDeps = wireWith.requiredKeys
      loggerHook.log(s"FactoryExecutor: Product dependencies are $productDeps")

      val narrowedContext = context.narrow(productDeps)
      loggerHook.log(s"FactoryExecutor: context narrowed to $narrowedContext, requested dependencies were $productDeps")

      val argsWithKeys = methodArguments.zip(args).toMap

      val extendedContext = narrowedContext.extend(argsWithKeys)
      loggerHook.log(s"FactoryExecutor: context extended to $extendedContext by adding ${argsWithKeys.keys.toList}")

      loggerHook.log(s"FactoryExecutor: Here are args keys $args and dep keys $productDeps")

      val res: Seq[NewObjectOp] = executor.execute(extendedContext, FactoryTools.mkExecutableOp(op.target, wireWith, op.origin))
      loggerHook.log(s"FactoryExecutor: Successfully produced instances [${res.mkString(",")}]")

      res
    }
}

