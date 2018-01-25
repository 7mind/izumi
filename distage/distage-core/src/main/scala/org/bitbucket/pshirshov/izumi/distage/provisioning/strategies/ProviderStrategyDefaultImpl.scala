package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.exceptions.InvalidPlanException
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

class ProviderStrategyDefaultImpl extends ProviderStrategy  {
  def callProvider(context: ProvisioningContext, op: ExecutableOp.WiringOp.CallProvider): Seq[OpResult.NewInstance] = {

    import op._
    // TODO: here we depend on order
    val args = wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith) match {
          case Some(dep) =>
            dep
          case _ =>
            throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: Class: $target, dependency: $key")
        }
    }

    val instance = wiring.provider.apply(args: _*)
    Seq(OpResult.NewInstance(target, instance))
  }
}

object ProviderStrategyDefaultImpl {
  final val instance = new ProviderStrategyDefaultImpl()
}