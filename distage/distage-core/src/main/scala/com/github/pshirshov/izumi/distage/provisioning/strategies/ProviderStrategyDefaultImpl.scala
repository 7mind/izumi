package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.LoggerHook
import com.github.pshirshov.izumi.distage.model.exceptions.InvalidPlanException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProviderStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{FactoryExecutor, OpResult, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class ProviderStrategyDefaultImpl(loggerHook: LoggerHook) extends ProviderStrategy  {
  def callProvider(context: ProvisioningKeyProvider, executor: OperationExecutor, op: WiringOp.CallProvider): Seq[OpResult.NewInstance] = {

    // TODO: here we depend on order of .associations and Callable.argTypes being the same
    val args: Seq[RuntimeDIUniverse.TypedRef[_]] = op.wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith) match {
          case Some(dep) =>
            RuntimeDIUniverse.TypedRef(dep, key.wireWith.tpe)
          // FIXME: this is specifically to support FactoryStrategyMacro
          case _ if key.wireWith == RuntimeDIUniverse.DIKey.get[FactoryExecutor] =>
            RuntimeDIUniverse.TypedRef(mkExecutor(context, executor))
          case _ =>
            throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
                s" but the dependency has not been initialized: Class: $op.target, dependency: $key")
        }
    }

    val instance = op.wiring.provider.unsafeApply(args: _*)
    Seq(OpResult.NewInstance(op.target, instance))
  }

  private def mkExecutor(context: ProvisioningKeyProvider, executor: OperationExecutor): FactoryExecutor =
    (args, step) => {
      loggerHook.log(s"FactoryExecutor: Executing $step with ${args.values.toList} in context $context")

      val productDeps = step.wiring.associations.map(_.wireWith)
      loggerHook.log(s"FactoryExecutor: Product dependencies are $productDeps")

      val narrowedContext = context.narrow(productDeps.toSet)
      loggerHook.log(s"FactoryExecutor: context narrowed to $narrowedContext, requested dependencies were $productDeps")

      val extendedContext = narrowedContext.extend(args)
      loggerHook.log(s"FactoryExecutor: context extended to $extendedContext by adding ${args.keys.toList}")

      loggerHook.log(s"FactoryExecutor: Here are args keys $args and dep keys $productDeps")

      val res: Seq[OpResult] = executor.execute(extendedContext, step)
      loggerHook.log(s"FactoryExecutor: Successfully produced instances [${res.mkString(",")}]")

      res
    }
}

