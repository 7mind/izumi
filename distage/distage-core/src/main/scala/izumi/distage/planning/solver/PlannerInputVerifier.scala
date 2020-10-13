package izumi.distage.planning.solver

import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.Axis.AxisPoint
import izumi.distage.model.definition.conflicts.{Annotated, Node}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.plan.{ExecutableOp, Roots}
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.solver.SemigraphSolver.SemiEdgeSeq
import izumi.functional.IzEither._
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.graphs.WeakEdge

import scala.collection.mutable

class PlannerInputVerifier(
  preps: GraphPreparations
) {
  import PlannerInputVerifier._
  def verify(input: PlannerInput): Either[List[PlanIssue], Unit] = {
    val ops = preps.computeOperationsUnsafe(input).toSeq
    val allAxis: Map[String, Set[String]] = ops.flatMap(_._1.axis).groupBy(_.axis).map {
      case (axis, points) =>
        (axis, points.map(_.value).toSet)
    }
    val (mutators, defns) = ops.partition(_._3.isMutator)
    val justOps = defns.map { case (k, op, _) => k -> op }
    val setOps = preps
      .computeSetsUnsafe(justOps)
      .map {
        case (k, (s, _)) =>
          (Annotated(k, None, Set.empty), Node(s.members, s))

      }.toMultimapView
      .map {
        case (k, v) =>
          val members = v.flatMap(_.deps).toSet
          (k, Node(members, v.head.meta.copy(members = members): InstantiationOp))
      }
      .toMap

    val opsMatrix: Seq[(Annotated[DIKey], Node[DIKey, InstantiationOp])] = preps.toDeps(justOps)

    val matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp] = SemiEdgeSeq(opsMatrix ++ setOps)

    val toTrace: ImmutableMultiMap[DIKey, (ExecutableOp.InstantiationOp, Set[AxisPoint])] = defns.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap

    val roots: Set[DIKey] = preps.getRoots(input, justOps)

    val weakSetMembers: Set[WeakEdge[DIKey]] = preps.findWeakSetMembers(setOps, matrix, roots)

    for {
      _ <- trace(allAxis, mutable.HashSet.empty, toTrace, weakSetMembers)(roots, Set.empty)
      // TODO: for both sets and mutators we need to filter out wrong ops and check for missing imports
      // TODO: consider weak sets properly
    } yield {}
  }

  private def trace(
    allAxis: Map[String, Set[String]],
    visited: mutable.HashSet[DIKey],
    matrix: ImmutableMultiMap[DIKey, (ExecutableOp.InstantiationOp, Set[AxisPoint])],
    weakSetMembers: Set[WeakEdge[DIKey]],
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
              ops <- matrix.get(key).toRight(List(MissingImport(key)))
              withoutImpossibleActivations = ops.filter(op => ac.allValid(op._2))
              withoutDefinedActivations = withoutImpossibleActivations.map {
                case (op, activations) =>
                  (op, activations diff currentActivation)
              }
              // we ignore activations for set definitions
              setOps = withoutDefinedActivations.collect({ case (s: CreateSet, a) => s })
              mergedSets <- setOps
                .groupBy(_.target)
                .map {
                  case (_, o) =>
                    for {
                      members <- o
                        .flatMap(_.members).map {
                          m =>
                            matrix.get(m) match {
                              case Some(value) if value.size == 1 =>
                                Right((m, ac.allValid(value.head._2)))
                              case Some(value) =>
                                Left(List(InconsistentSetMembers(value.map(_._1).toSeq)))
                              case None =>
                                Left(List(MissingImport(m)))
                            }
                        }.biAggregate
                    } yield {
                      (o.head.copy(members = members.filter(_._2).map(_._1)), Set.empty[AxisPoint])
                    }
                }.biAggregate
              withMergedSets = withoutDefinedActivations.filterNot(_._1.isInstanceOf[CreateSet]) ++ mergedSets
              _ <-
                if (withMergedSets.isEmpty) {
                  Left(List(MissingImport(key)))
                } else {
                  Right(())
                }
              next <- checkConflicts(allAxis, withMergedSets, weakSetMembers)
              _ <- Right(visited.add(key))
              _ <- next.map {
                case (nextActivation, nextDeps) =>
                  trace(allAxis, visited, matrix, weakSetMembers)(nextDeps, currentActivation ++ nextActivation)
              }.biAggregate
            } yield {}
          }

      }
      .biAggregate
      .map(_ => ())
  }

  def checkConflicts(
    allAxis: Map[String, Set[String]],
    withoutDefinedActivations: Set[(ExecutableOp.InstantiationOp, Set[AxisPoint])],
    weakSetMembers: Set[WeakEdge[DIKey]],
  ): Either[List[PlanIssue], Seq[(Set[AxisPoint], Set[DIKey])]] = {

    for {
      issues <- Right(
        checkForUnsaturatedAxis(allAxis, withoutDefinedActivations) ++
        checkForConflictingAxis(withoutDefinedActivations) ++
        checkForConflictingBindings(withoutDefinedActivations)
      )
      _ <-
        if (issues.nonEmpty) {
          Left(issues)
        } else {
          Right(())
        }
      next = withoutDefinedActivations.toSeq.flatMap {
        case (op, activations) =>
          // TODO: I'm not sure if it's "correct" to "activate" all the points together but it simplifies things greatly
          op match {
            case cs: ExecutableOp.CreateSet =>
              val members = cs.members.filterNot(m => weakSetMembers.contains(WeakEdge(m, cs.target)))
              Seq((Set.empty[AxisPoint], members))
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

  def checkForConflictingAxis(
    ops: Set[(ExecutableOp.InstantiationOp, Set[AxisPoint])]
  ): List[PlanIssue] = {
    ops
      .toList
      .flatMap {
        case (op, acts) =>
          val bad = acts.groupBy(_.axis).filter(_._2.size > 1)
          if (bad.isEmpty) {
            List.empty
          } else {
            List(ConflictingActivations(op, bad))
          }

      }
  }

  def checkForConflictingBindings(
    ops: Set[(ExecutableOp.InstantiationOp, Set[AxisPoint])]
  ): List[PlanIssue] = {
    // TODO: this method should fail in case any bindings in the set are indistinguishable
    // TODO: in case we implement precedence rules the implementation should change

    val compatible = mutable.ArrayBuffer.empty[(ExecutableOp.InstantiationOp, Set[AxisPoint])]
    compatible.appendAll(ops.headOption)

    val incompatible = mutable.ArrayBuffer.empty[(ExecutableOp.InstantiationOp, Set[AxisPoint])]

    // TODO: quadratic, better approaches possible
    for ((op, a) <- ops.drop(1)) {
      if (compatible.forall(c => isCompatible(c._2, a))) {
        compatible.append((op, a))
      } else {
        incompatible.append((op, a))
      }
    }

    if (incompatible.isEmpty) {
      List.empty
    } else {
      List(ConflictingBindings(ops.head._1.target, incompatible.map(_._1).toSeq))
    }
  }

  def isCompatible(c: Set[AxisPoint], a: Set[AxisPoint]): Boolean = {
    val common = c.intersect(a)
    val cDiff = c.diff(common)
    val aDiff = a.diff(common)
    !(cDiff.isEmpty && aDiff.isEmpty)
  }

  def checkForUnsaturatedAxis(
    allAxis: Map[String, Set[String]],
    ops: Set[(ExecutableOp.InstantiationOp, Set[AxisPoint])],
  ): List[PlanIssue] = {
    val currentAxis = ops.flatMap(_._2.map(_.axis))
    // TODO: this method should fail in case there are some missing/uncovered points on any of the axis
    val toTest = ops.map(_._2.to(mutable.Set))
    val issues = mutable.ArrayBuffer.empty[PlanIssue]

    currentAxis.foreach {
      a =>
        val definedValues = toTest.flatMap(_.filter(_.axis == a)).map(_.value)
        val diff = allAxis.get(a).map(_.diff(definedValues)).toSeq.flatten
        if (diff.nonEmpty) {
          // TODO: quadratic
          if (toTest.forall(set => set.map(_.axis).contains(a))) {
            issues.append(UnsaturatedAxis(ops.head._1.target, a, diff))
          }
        }
    }

    issues.toList
  }
}

object PlannerInputVerifier {
  sealed trait PlanIssue
  case class MissingImport(key: DIKey) extends PlanIssue
  case class ConflictingBindings(key: DIKey, ops: Seq[ExecutableOp.InstantiationOp]) extends PlanIssue
  case class ConflictingActivations(op: ExecutableOp.InstantiationOp, bad: Map[String, Set[AxisPoint]]) extends PlanIssue
  case class UnsaturatedAxis(key: DIKey, name: String, missing: Seq[String]) extends PlanIssue
  case class InconsistentSetMembers(ops: Seq[ExecutableOp.InstantiationOp]) extends PlanIssue

}
