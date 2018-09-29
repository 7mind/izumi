package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.{IncompatibleTypesException, MissingRefException, SanityCheckFailedException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.CreateSet
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.SetStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class SetStrategyDefaultImpl extends SetStrategy {
  def makeSet(context: ProvisioningKeyProvider, op: CreateSet): Seq[OpResult.NewInstance] = {
    // target is guaranteed to be a Set
    val scalaCollectionSetType = SafeType.get[collection.Set[_]]
    val erasure = scalaCollectionSetType.tpe.typeSymbol

    if (!op.tpe.tpe.baseClasses.contains(erasure)) {
      throw new IncompatibleTypesException("Tried to create make a Set with a non-Set type! " +
        s"For ${op.target} expected ${op.tpe} to be a sub-class of $scalaCollectionSetType, but it isn't!"
        , scalaCollectionSetType
        , op.tpe)
    }

    val keyType = op.tpe.tpe.typeArgs match {
      case head :: Nil =>
        SafeType.apply(head)
      case _ =>
        throw new IncompatibleTypesException("Expected to be a set type but has no parameters", scalaCollectionSetType, op.tpe)
    }

    val badKeys = op.members.filterNot {
      key =>
        (key.tpe weak_<:< keyType) || (key.tpe weak_<:< op.tpe)
    }

    if (badKeys.nonEmpty) {
      throw new SanityCheckFailedException(s"Target set ${op.target} expects ${op.tpe} but got members $badKeys")
    }

    val fetched = op.members
      .map(m => (m, context.fetchKey(m)))

    val newSet = fetched.flatMap {
      case (m, Some(value)) if m.tpe weak_<:< op.tpe => // in case member type <:< set type we just add
        value.asInstanceOf[collection.Set[Any]]
      case (_, Some(value)) =>
        Set(value)
      case (m, None) =>
        throw new MissingRefException(s"Failed to fetch set element $m", Set(m), None)
    }

    Seq(OpResult.NewInstance(op.target, newSet))
  }
}


