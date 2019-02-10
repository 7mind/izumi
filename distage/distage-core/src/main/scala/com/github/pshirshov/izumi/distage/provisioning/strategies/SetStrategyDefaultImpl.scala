package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.{IncompatibleTypesException, MissingRefException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.CreateSet
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.SetStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{ExecutableOpResult, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.provisioning.ProvisionOperationVerifier

import scala.collection.immutable.ListSet

class SetStrategyDefaultImpl
(
  verifier: ProvisionOperationVerifier,
) extends SetStrategy {
  def makeSet(context: ProvisioningKeyProvider, op: CreateSet): Seq[ExecutableOpResult.NewInstance] = {
    // target is guaranteed to be a Set
    val scalaCollectionSetType = SafeType.get[collection.Set[_]]
    val setErasure = scalaCollectionSetType.tpe.typeSymbol

    if (!op.tpe.tpe.baseClasses.contains(setErasure)) {
      throw new IncompatibleTypesException("Tried to create a Set with a non-Set type! " +
        s"For ${op.target} expected ${op.tpe} to be a sub-class of $scalaCollectionSetType, but it isn't!"
        , scalaCollectionSetType
        , op.tpe)
    }

    val keyType = op.tpe.tpe.typeArgs match {
      case head :: Nil =>
        SafeType(head)
      case _ =>
        throw new IncompatibleTypesException("Expected to be a set type but has no parameters", scalaCollectionSetType, op.tpe)
    }

    val fetched = op.members
      .map(m => (m, context.fetchKey(m, byName = false)))

    val newSet = fetched.flatMap {
      case (m, Some(value)) if m.tpe == op.tpe =>
        // in case member type == set type we just merge them
        value.asInstanceOf[collection.Set[Any]]
      case (m, Some(value)) if m.tpe.tpe.baseClasses.contains(setErasure) && m.tpe.tpe.typeArgs.headOption.exists(SafeType(_) weak_<:< keyType) =>
        // if member set element type is compatible with this set element type we also just merge them
        value.asInstanceOf[collection.Set[Any]]
      case (m, Some(value)) if m.tpe weak_<:< keyType =>
        ListSet(value)
      case (m, Some(value)) =>
        // Member key type may not conform to set parameter (valid case for autosets) while implementation is still valid
        // so sanity check has to be done against implementation type.
        // Though at this point we have no accessible static descriptor of set member implementation type
        // This check itself is kinda excessive but in case it fails it would be a very bad signal
        verifier.verify(DIKey.TypeKey(keyType), op.members, value, s"Set ${op.target} has incompatible member $m. Expected type: $keyType")
        ListSet(value)
      case (m, None) =>
        throw new MissingRefException(s"Failed to fetch set element $m", Set(m), None)
    }

    Seq(ExecutableOpResult.NewInstance(op.target, newSet))
  }
}


