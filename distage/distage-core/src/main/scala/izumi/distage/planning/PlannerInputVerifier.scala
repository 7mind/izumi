package izumi.distage.planning

import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.Axis.AxisPoint
import izumi.distage.model.definition.conflicts.Annotated
import izumi.distage.model.plan.{ExecutableOp, Roots}
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.PlannerInputVerifier.PlanIssue
import izumi.distage.planning.solver.{ActivationChoices, GraphPreparations}
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.collections.IzCollections._
import izumi.functional.IzEither._

import scala.collection.mutable

class PlannerInputVerifier(
  preps: GraphPreparations
) {
  import PlannerInputVerifier._
  def verify(input: PlannerInput): Either[List[PlanIssue], Unit] = {
    val ops = preps.computeOperationsUnsafe(input).toSeq
    val (mutators, defns) = ops.partition(_._3.isMutator)
    val justOps = defns.map { case (k, op, _) => k -> op }
    val setOps = preps.computeSetsUnsafe(justOps)
    val toTrace: ImmutableMultiMap[DIKey, (ExecutableOp.InstantiationOp, Set[AxisPoint])] = defns.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap

    val roots = input.roots match {
      case Roots.Of(roots) =>
        roots.toSet
      case Roots.Everything =>
        ops.map(_._1.key).toSet
    }

    for {
      _ <- trace(mutable.HashSet.empty, toTrace)(roots, Set.empty)
      // TODO: for both sets and mutators we need to filter out wrong ops and check for missing imports
      // TODO: consider weak sets properly
    } yield {}
  }

  private def trace(
    visited: mutable.HashSet[DIKey],
    matrix: ImmutableMultiMap[DIKey, (ExecutableOp.InstantiationOp, Set[AxisPoint])],
  )(current: Set[DIKey],
    currentActivation: Set[AxisPoint],
  ): Either[List[PlanIssue], Unit] = {
    current
      .map {
        key =>
          if (visited.contains(key)) {
            Right(())
          } else {
            for {
              ac <- Right(ActivationChoices(currentActivation))
              ops <- matrix.get(key) match {
                case Some(value) =>
                  Right(value)
                case None =>
                  Right(Set.empty[(ExecutableOp.InstantiationOp, Set[AxisPoint])]) // .toRight(List(MissingImport(key)))
              }
              withoutImpossibleActivations = ops.filter(op => ac.allValid(op._2))
              withoutDefinedActivations = withoutImpossibleActivations.map {
                case (op, activations) =>
                  (op, activations diff currentActivation)
              }
              next <- checkConflicts(withoutDefinedActivations)
              _ <- Right(visited.add(key))
              sub <- next.map {
                case (nextActivation, nextDeps) =>
                  trace(visited, matrix)(nextDeps, currentActivation ++ nextActivation)
              }.biAggregate
            } yield {}
          }

      }
      .biAggregate
      .map(_ => ())
  }

  def checkConflicts(withoutDefinedActivations: Set[(ExecutableOp.InstantiationOp, Set[AxisPoint])]): Either[List[PlanIssue], Seq[(Set[AxisPoint], Set[DIKey])]] = {

    for {
      _ <- Right(())
      next = withoutDefinedActivations.toSeq.flatMap {
        case (op, activations) =>
          op match {
            case _: ExecutableOp.CreateSet =>
              Seq.empty
            case op: ExecutableOp.WiringOp =>
              Seq((activations, preps.toDep(op).deps))

            case op: ExecutableOp.MonadicOp =>
              Seq((activations, preps.toDep(op).deps))

          }
      }
    } yield {
      next
    }
  }
}

object PlannerInputVerifier {
  sealed trait PlanIssue
  case class MissingImport(key: DIKey) extends PlanIssue
  case class ConflictingBindings() extends PlanIssue
  case class UnsaturatedAxis() extends PlanIssue

}
