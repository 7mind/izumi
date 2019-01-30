package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.InvalidPlanException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProviderStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{ExecutableOpResult, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class ProviderStrategyDefaultImpl extends ProviderStrategy  {
  def callProvider(context: ProvisioningKeyProvider, executor: OperationExecutor, op: WiringOp.CallProvider): Seq[ExecutableOpResult.NewInstance] = {

    val args: Seq[RuntimeDIUniverse.TypedRef[_]] = op.wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith, key.isByName) match {
          case Some(dep) =>
            RuntimeDIUniverse.TypedRef(dep, key.wireWith.tpe)
          case _ =>
            throw new InvalidPlanException("The impossible happened! Tried to instantiate class," +
                s" but the dependency has not been initialized: Class: $op.target, dependency: $key")
        }
    }

    val instance = op.wiring.provider.unsafeApply(args: _*)
    Seq(ExecutableOpResult.NewInstance(op.target, instance))
  }
}

