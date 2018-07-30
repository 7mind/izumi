package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.LoggerHook
import com.github.pshirshov.izumi.distage.model.exceptions.InvalidPlanException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.{FactoryExecutor, FactoryProviderStrategy}
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.provisioning.FactoryTools

class FactoryProviderStrategyDefaultImpl(loggerHook: LoggerHook) extends FactoryProviderStrategy  {
  def callFactoryProvider(context: ProvisioningKeyProvider, executor: OperationExecutor, op: WiringOp.CallFactoryProvider): Seq[OpResult.NewInstance] = {

    val args: Seq[TypedRef[_]] = op.wiring.providerArguments.map {
      key =>
        context.fetchKey(key.wireWith) match {
          case Some(dep) =>
            TypedRef(dep, key.wireWith.tpe)
          case _ if key.wireWith == DIKey.get[FactoryExecutor] =>
            TypedRef(mkExecutor(context, executor, op.wiring.factoryIndex, op))
          case _ =>
            throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
                s" but the dependency has not been initialized: Class: $op.target, dependency: $key")
        }
    }

    val instance = op.wiring.provider.unsafeApply(args: _*)
    Seq(OpResult.NewInstance(op.target, instance))
  }

  private def mkExecutor(context: ProvisioningKeyProvider, executor: OperationExecutor, factoryIndex: Map[Int, Wiring.FactoryFunction.WithContext], op: WiringOp.CallFactoryProvider): FactoryExecutor =
    (idx, args) => {
      loggerHook.log(s"FactoryExecutor: Start! Looking up method index $idx in $factoryIndex")

      val step = factoryIndex(idx)

      loggerHook.log(s"FactoryExecutor: Executing method $step with ${args.toList} in context $context")

      val productDeps = step.wireWith.requiredKeys
      loggerHook.log(s"FactoryExecutor: Product dependencies are $productDeps")

      val narrowedContext = context.narrow(productDeps)
      loggerHook.log(s"FactoryExecutor: context narrowed to $narrowedContext, requested dependencies were $productDeps")

      val argsWithKeys = step.methodArguments.zip(args).toMap

      val extendedContext = narrowedContext.extend(argsWithKeys)
      loggerHook.log(s"FactoryExecutor: context extended to $extendedContext by adding ${argsWithKeys.keys.toList}")

      loggerHook.log(s"FactoryExecutor: Here are args keys $args and dep keys $productDeps")

      val res: Seq[OpResult] = executor.execute(extendedContext, FactoryTools.mkExecutableOp(op.target, step.wireWith, op.origin))
      loggerHook.log(s"FactoryExecutor: Successfully produced instances [${res.mkString(",")}]")

      res
    }
}

