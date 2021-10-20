package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.interpretation.{IncompatibleTypesException, MissingRefException}
import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.strategies.SetStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.distage.model.reflection.*
import izumi.fundamentals.collections.OrderedSetShim

import scala.collection.Iterable

class SetStrategyDefaultImpl extends SetStrategy {
  def makeSet(context: ProvisioningKeyProvider, op: CreateSet): Seq[NewObjectOp.NewInstance] = {
    // target is guaranteed to be a Set
    val scalaCollectionSetType = SafeType.get[collection.Set[?]]

    val keyType = op.target.tpe.tag.typeArgs.headOption.getOrElse {
      throw new IncompatibleTypesException("Expected to be a set type but has no parameters", scalaCollectionSetType, op.target.tpe)
    }

    val allOrderedInstances = context.instances.keySet

    // this assertion is correct though disabled because it's weird and, probably, unnecessary slow
    /*assert(
      Set("scala.collection.mutable.LinkedHashMap$DefaultKeySet", "scala.collection.mutable.LinkedHashMap$LinkedKeySet").contains(allOrderedInstances.getClass.getName),
      s"got: ${allOrderedInstances.getClass.getName}",
    )*/

    val orderedMembersSeq: Seq[DIKey] = allOrderedInstances.intersect(op.members).toSeq
    val fetched = orderedMembersSeq.map(m => (m, context.fetchKey(m, makeByName = false)))

    val newSet = fetched.flatMap {
      case (m, Some(value)) if m.tpe =:= op.target.tpe =>
        // in case member type == set type we just merge them
        value.asInstanceOf[Iterable[Any]]

      case (m, Some(value)) if m.tpe.tag.withoutArgs <:< scalaCollectionSetType.tag.withoutArgs && m.tpe.tag.typeArgs.headOption.exists(_ <:< keyType) =>
        // if member set element type is compatible with this set element type we also just merge them
        value.asInstanceOf[Iterable[Any]]

      case (_, Some(value)) =>
        Iterable(value)

      case (m, None) =>
        throw new MissingRefException(s"Failed to fetch set element $m", Set(m), None)
    }

    val asSet = new OrderedSetShim[Any](newSet.distinct)
    Seq(NewObjectOp.NewInstance(op.target, op.instanceType, asSet))
  }
}
