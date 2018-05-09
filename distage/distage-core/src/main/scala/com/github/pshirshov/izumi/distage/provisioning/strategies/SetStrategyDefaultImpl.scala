package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.IncompatibleTypesException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.CreateSet
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.SetStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class SetStrategyDefaultImpl extends SetStrategy {
  def makeSet(context: ProvisioningContext, op: CreateSet): Seq[OpResult.NewInstance] = {
    import op._
    // target is guaranteed to be a Set
    val scalaCollectionSetType = RuntimeDIUniverse.SafeType.get[collection.Set[_]]
    val erasure = scalaCollectionSetType.tpe.typeSymbol

    if (!tpe.tpe.baseClasses.contains(erasure)) {
      throw new IncompatibleTypesException("Tried to create make a Set with a non-Set type! " +
        s"For $target expected $tpe to be a sub-class of $scalaCollectionSetType, but it isn't!"
        , scalaCollectionSetType
        , tpe)
    }

    val newSet = op.members.map(m => context.fetchKey(m).get)

    Seq(OpResult.NewInstance(target, newSet))
  }
}


