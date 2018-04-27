package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.InvalidPlanException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.OpResult.NewInstance
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProviderStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{FactoryExecutor, OpResult, OperationExecutor, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

class ProviderStrategyDefaultImpl extends ProviderStrategy  {
  def callProvider(context: ProvisioningContext, executor: OperationExecutor, op: WiringOp.CallProvider): Seq[OpResult.NewInstance] = {

    // TODO: here we depend on order of .associations and Callable.argTypes being the same
    val args = op.wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith) match {
          case Some(dep) =>
            dep
          // support FactoryStrategyMacro
          case _ if key.wireWith == RuntimeUniverse.DIKey.get[FactoryExecutor] =>
            mkExecutor(context, executor)
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
      // TOOD: logger in DI
      System.err println s"FactoryExecutor! Executing $step with ${args.values.toList}"
      val narrowedContext = context.narrow(step.wiring.associations.map(_.wireWith).toSet)
      System.err println s"FactoryExecutor! context narrowed to $narrowedContext"
      val extendedContext = narrowedContext.extend(args)
      System.err println s"FactoryExecutor! context extended to $extendedContext by adding ${args.keys.toList}"

      val sideBySide = args.keySet.zip(step.wiring.associations.map(_.wireWith))
      System.err println s"Here are args and dep keys side by side:\n${sideBySide.mkString("\n")}"
      assert(sideBySide.forall {case (x,y) => x equals y})

      val res: Seq[OpResult] = executor.execute(extendedContext, step)
      System.err println s"FactoryExecutor! Successfully produced ${res.map {
        case NewInstance(key, instance) => s"new instance $instance for $key!"
      }.mkString}"
      res
    }
}

