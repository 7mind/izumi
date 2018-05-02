package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.LoggerHook
import com.github.pshirshov.izumi.distage.model.exceptions.InvalidPlanException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProviderStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{FactoryExecutor, OpResult, OperationExecutor, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class ProviderStrategyDefaultImpl(loggerHook: LoggerHook) extends ProviderStrategy  {
  def callProvider(context: ProvisioningContext, executor: OperationExecutor, op: WiringOp.CallProvider): Seq[OpResult.NewInstance] = {

    // TODO: here we depend on order of .associations and Callable.argTypes being the same
    val args: Seq[RuntimeDIUniverse.TypedRef[_]] = op.wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith) match {
          case Some(dep) =>
            RuntimeDIUniverse.TypedRef(dep, key.wireWith.symbol)
          // FIXME: specific support for FactoryStrategyMacro
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

  private def mkExecutor(context: ProvisioningContext, executor: OperationExecutor): FactoryExecutor =
    (args, step) => {
      loggerHook.log(s"FactoryExecutor! Executing $step with ${args.values.toList}")

      val productDeps = step.wiring.associations.map(_.wireWith)

      val narrowedContext = context.narrow(productDeps.toSet)
      loggerHook.log(s"FactoryExecutor! context narrowed to $narrowedContext, requested dependencies were $productDeps")

      val extendedContext = narrowedContext.extend(args)
      loggerHook.log(s"FactoryExecutor! context extended to $extendedContext by adding ${args.keys.toList}")

      val sideBySide = args.keySet.zip(productDeps)

      loggerHook.log(s"Here are args and dep keys side by side:\n${sideBySide.mkString("\n")}")
      assert(sideBySide forall { case (x, y) => x equals y })

      val res: Seq[OpResult] = executor.execute(extendedContext, step)
      loggerHook.log(s"FactoryExecutor! Successfully produced instances [${res.mkString(",")}]")

      res
    }
}

