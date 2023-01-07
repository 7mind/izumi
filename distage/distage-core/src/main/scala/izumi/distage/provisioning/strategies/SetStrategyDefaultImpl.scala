package izumi.distage.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.CreateSet
import izumi.distage.model.provisioning.strategies.SetStrategy
import izumi.distage.model.provisioning.{NewObjectOp, ProvisioningKeyProvider}
import izumi.distage.model.reflection.*
import izumi.fundamentals.collections.OrderedSetShim
import izumi.reflect.TagK

class SetStrategyDefaultImpl extends SetStrategy {
  def makeSet[F[_]: TagK](context: ProvisioningKeyProvider, op: CreateSet)(implicit F: QuasiIO[F]): F[Either[ProvisionerIssue, Seq[NewObjectOp]]] = {
    import izumi.functional.IzEither.*
    // target is guaranteed to be a Set
    val scalaCollectionSetType = SafeType.get[collection.Set[?]]

    F.pure(for {
      keyType <- op.target.tpe.tag.typeArgs.headOption.toRight(ProvisionerIssue.IncompatibleTypes(op.target, scalaCollectionSetType, op.target.tpe))
      allOrderedInstances = context.instances.keySet
      orderedMembersSeq = allOrderedInstances.intersect(op.members).toSeq
      fetched = orderedMembersSeq.map(m => (m, context.fetchKey(m, makeByName = false)))
      newSet <- fetched
        .map {
          case (m, Some(value)) if m.tpe =:= op.target.tpe =>
            // in case member type == set type we just merge them
            Right(value.asInstanceOf[Iterable[Any]])

          case (m, Some(value)) if m.tpe.tag.withoutArgs <:< scalaCollectionSetType.tag.withoutArgs && m.tpe.tag.typeArgs.headOption.exists(_ <:< keyType) =>
            // if member set element type is compatible with this set element type we also just merge them
            Right(value.asInstanceOf[Iterable[Any]])

          case (_, Some(value)) =>
            Right(Iterable(value))

          case (m, None) =>
            Left(List(m))
        }.biFlatAggregate.left.map(missing => ProvisionerIssue.MissingRef(op.target, "set element", missing.toSet))
    } yield {
      val asSet = new OrderedSetShim[Any](newSet.distinct)
      Seq(NewObjectOp.NewInstance(op.target, op.instanceType, asSet))
    })

    // this assertion is correct though disabled because it's weird and, probably, unnecessary slow
    /*assert(
      Set("scala.collection.mutable.LinkedHashMap$DefaultKeySet", "scala.collection.mutable.LinkedHashMap$LinkedKeySet").contains(allOrderedInstances.getClass.getName),
      s"got: ${allOrderedInstances.getClass.getName}",
    )*/

  }
}
