package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.{IncompatibleTypesException, MissingRefException}
import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.strategies.SetStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.immutable.ListSet

class SetStrategyDefaultImpl extends SetStrategy {
  def makeSet(context: ProvisioningKeyProvider, op: CreateSet): Seq[NewObjectOp.NewInstance] = {
    // target is guaranteed to be a Set
    val scalaCollectionSetType = SafeType.get[collection.Set[_]]
    val setErasure = scalaCollectionSetType.use(_.typeSymbol)

    val keyType = op.target.tpe.use(_.typeArgs match {
      case head :: Nil =>
        SafeType(head)
      case _ =>
        throw new IncompatibleTypesException("Expected to be a set type but has no parameters", scalaCollectionSetType, op.element)
    })

    val fetched = op.members
      .map(m => (m, context.fetchKey(m, makeByName = false)))

    val newSet = fetched.flatMap {
      case (m, Some(value)) if m.tpe =:= op.target.tpe =>
        // in case member type == set type we just merge them
        value.asInstanceOf[collection.Set[Any]]
      case (m, Some(value)) if m.tpe.use(_.baseClasses.contains(setErasure)) && m.tpe.use(_.typeArgs.headOption.exists(SafeType(_) <:< keyType)) =>
        // if member set element type is compatible with this set element type we also just merge them
        value.asInstanceOf[collection.Set[Any]]
      case (_, Some(value)) =>
        ListSet(value)
      case (m, None) =>
        throw new MissingRefException(s"Failed to fetch set element $m", Set(m), None)
    }

    Seq(NewObjectOp.NewInstance(op.target, newSet))
  }
}
