package izumi.distage.planning.solver

import izumi.distage.model.definition.Axis.AxisPoint
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.definition.conflicts.{Annotated, Node}
import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.{ExecutableOp, Roots}
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.BindingTranslator
import izumi.distage.planning.solver.PlanVerifier.{PlanIssue, PlanVerifierResult}
import izumi.distage.planning.solver.PlanVerifier.PlanIssue._
import izumi.distage.planning.solver.SemigraphSolver.SemiEdgeSeq
import izumi.functional.IzEither._
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptyMap, NonEmptySet}
import izumi.fundamentals.graphs.WeakEdge

import scala.annotation.nowarn
import scala.collection.mutable

@nowarn("msg=Unused import")
class PlanVerifier(
  preps: GraphPreparations
) {
  import scala.collection.compat._

  def verify(bindings: ModuleBase, roots: Roots): PlanVerifierResult = {
    val ops = preps.computeOperationsUnsafe(bindings).toSeq
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

    val toTrace: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])] = defns.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap

    val rootKeys: Set[DIKey] = preps.getRoots(roots, justOps)

    val weakSetMembers: Set[WeakEdge[DIKey]] = preps.findWeakSetMembers(setOps, matrix, rootKeys)

    val justMutators: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])] = mutators.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap

    val mutVisited: mutable.HashSet[DIKey] = mutable.HashSet.empty[DIKey]
    val issues = trace(allAxis, mutVisited, toTrace, weakSetMembers, justMutators)(rootKeys.map(r => (r, r)), Set.empty).fold(_.toSet, _ => Set.empty[PlanIssue])

    PlanVerifierResult(issues, mutVisited.toSet)
  }

  private def trace(
    allAxis: Map[String, Set[String]],
    visited: mutable.HashSet[DIKey],
    matrix: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    weakSetMembers: Set[WeakEdge[DIKey]],
    justMutators: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
  )(current: Set[(DIKey, DIKey)],
    currentActivation: Set[AxisPoint],
  ): Either[List[PlanIssue], Unit] = {
    current.map {
      case (key, dependee) =>
        if (visited.contains(key)) {
          Right(())
        } else {
          val ac = ActivationChoices(currentActivation)
          def allImportingBindings(d: DIKey): Set[(DIKey, OperationOrigin)] = {
            // FIXME: reuse formatting from conflictingAxisTagsHint
            matrix
              .getOrElse(d, Set.empty)
              .collect {
                case (op, activations) if activations.subsetOf(currentActivation) && (op match {
                      case CreateSet(_, members, _) => members
                      case op: ExecutableOp.WiringOp => op.wiring.requiredKeys
                      case op: ExecutableOp.MonadicOp => Set(op.effectKey)
                    }).contains(key) =>
                  op.target -> op.origin.value
              }
          }
          for {
            ops <- matrix.get(key).toRight(List(MissingImport(key, dependee, allImportingBindings(dependee))))
            withoutDefinedActivations = {
              val withoutImpossibleActivations = ops.filter(ac allValid _._2)
              withoutImpossibleActivations.map {
                case (op, activations) =>
                  (op, activations diff currentActivation, activations)
              }
            }
            // we ignore activations for set definitions
            mergedSets <- {
              val setOps = withoutDefinedActivations.collect { case (s: CreateSet, _, _) => s }
              setOps
                .groupBy(_.target)
                .map {
                  case (_, o) =>
                    for {
                      members <- o
                        .flatMap(_.members).map {
                          m =>
                            matrix.get(m) match {
                              case Some(value) if value.sizeIs == 1 =>
                                Right((m, ac.allValid(value.head._2)))
                              case Some(value) =>
                                Left(List(InconsistentSetMembers(m, NonEmptyList.unsafeFrom(value.iterator.map(_._1).toList))))
                              case None =>
                                Left(List(MissingImport(m, key, allImportingBindings(key))))
                            }
                        }.biAggregate
                    } yield {
                      (o.head.copy(members = members.filter(_._2).map(_._1)), Set.empty[AxisPoint], Set.empty[AxisPoint])
                    }
                }.biAggregate
            }
            withMergedSets = withoutDefinedActivations.filterNot(_._1.isInstanceOf[CreateSet]) ++ mergedSets
            _ <-
              if (withMergedSets.isEmpty) {
                val defined = ops.flatMap(op => op._2).groupBy(_.axis)
                val probablyUnsaturatedAxis = defined.flatMap {
                  case (axis, definedPoints) =>
                    NonEmptySet
                      .from(currentActivation.filter(_.axis == axis).diff(definedPoints))
                      .map(UnsaturatedAxis(key, axis, _))
                }

                if (probablyUnsaturatedAxis.nonEmpty) {
                  Left(probablyUnsaturatedAxis.toList)
                } else {
                  Left(List(MissingImport(key, dependee, allImportingBindings(dependee))))
                }
              } else {
                Right(())
              }
            mutators = justMutators.getOrElse(key, Set.empty).toSeq.filter(m => ac.allValid(m._2)).flatMap(m => depsOf(weakSetMembers)(m._1))
            next <- checkConflicts(allAxis, withMergedSets, weakSetMembers)
            _ <- Right(visited.add(key))
            _ <- next.map {
              case (nextActivation, nextDeps) =>
                trace(allAxis, visited, matrix, weakSetMembers, justMutators)((nextDeps ++ mutators).map(k => (k, key)), currentActivation ++ nextActivation)
            }.biAggregate
          } yield {}
        }
    }.biAggregateSequence
  }

  def checkConflicts(
    allAxis: Map[String, Set[String]],
    withoutDefinedActivations: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
    weakSetMembers: Set[WeakEdge[DIKey]],
  ): Either[List[PlanIssue], Seq[(Set[AxisPoint], Set[DIKey])]] = {

    val issues = {
      checkForUnsaturatedAxis(allAxis, withoutDefinedActivations) ++
      checkForConflictingAxisChoices(withoutDefinedActivations) ++
      checkForDuplicateActivations(withoutDefinedActivations) ++
      checkForUnsolvableConflicts(withoutDefinedActivations)
    }

    if (issues.nonEmpty) {
      Left(issues)
    } else {
      val next = withoutDefinedActivations.toSeq.map {
        case (op, activations, _) =>
          // TODO: I'm not sure if it's "correct" to "activate" all the points together but it simplifies things greatly
          val deps = depsOf(weakSetMembers)(op)

          val acts = op match {
            case _: ExecutableOp.CreateSet =>
              Set.empty[AxisPoint]
            case _ =>
              activations
          }
          (acts, deps)
      }
      Right(next)
    }
  }

  private def depsOf(weakSetMembers: Set[WeakEdge[DIKey]])(op: InstantiationOp): Set[DIKey] = {
    op match {
      case cs: CreateSet =>
        val members = cs.members.filterNot(m => weakSetMembers.contains(WeakEdge(m, cs.target)))
        members
      case op: ExecutableOp.WiringOp =>
        preps.toDep(op).deps
      case op: ExecutableOp.MonadicOp =>
        preps.toDep(op).deps
    }
  }

  def checkForConflictingAxisChoices(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[PlanIssue] = {
    ops
      .iterator
      .flatMap {
        case (op, activation, _) =>
          NonEmptyMap
            .from(activation.groupBy(_.axis).filter(_._2.sizeIs > 1))
            .map(ConflictingAxisChoices(op.target, op, _))
      }.toList
  }

  /** this method fails in case any bindings in the set have indistinguishable activations */
  def checkForDuplicateActivations(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[PlanIssue] = {
    val duplicateAxisMap = ops
      .groupBy(_._2)
      .filter(_._2.sizeIs > 1)
      .view.mapValues(NonEmptySet unsafeFrom _.map(_._1.origin.value))
      .toMap

    NonEmptyMap
      .from(duplicateAxisMap)
      .map(DuplicateActivations(ops.head._1.target, _))
      .toList
  }

  /** this method fails in case any bindings in the set have indistinguishable activations */
  def checkForUnsolvableConflicts(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[PlanIssue] = {
    // TODO: in case we implement precedence rules the implementation should change
//    val compatible = mutable.ArrayBuffer.empty[(InstantiationOp, Set[AxisPoint])]
//    if (ops.nonEmpty) compatible.append(ops.head)
//
//    val incompatible = List.newBuilder[(InstantiationOp, Set[AxisPoint])]
//
//    // TODO: quadratic, better approaches possible
//    for (opA @ (_, a) <- ops.drop(1)) {
//      val inc = compatible.collect { case c if !isCompatible(c._2, a) => c }
//      if (inc.isEmpty) {
//        compatible.append(opA)
//      } else {
//        incompatible ++= inc
//        incompatible += opA
//      }
//    }

    NonEmptySet.from(ops).fold(List.empty[PlanIssue]) {
      ops =>
        val commonAxes = ops
          .toSet.tail.iterator.map(_._3.map(_.axis)).foldLeft(
            Some(ops.head._3.map(_.axis)).filter(_.nonEmpty)
          ) {
            case (a, b) =>
              if (b.isEmpty) a
              else if (a.isEmpty /* && b.nonEmpty */ ) Some(b)
              else Some(a.get.intersect(b))
          }

        if (commonAxes.exists(_.isEmpty)) {
          List(UnsolvableConflict(ops.head._1.target, ops.map(t => t._1 -> t._3)))
        } else Nil
    }
  }

  def isCompatible(a: Set[AxisPoint], b: Set[AxisPoint]): Boolean = {
    val aAxis = a.map(_.axis)
    val bAxis = b.map(_.axis)
    aAxis.intersect(bAxis).nonEmpty || aAxis.isEmpty || bAxis.isEmpty
  }

  /** This method fails in case there are missing/uncovered points on any of the axis */
  def checkForUnsaturatedAxis(
    allAxis: Map[String, Set[String]],
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
  ): List[PlanIssue] = {
    val currentAxis: List[String] = ops.iterator.flatMap(_._2.map(_.axis)).toList
    val toTest: Set[Set[AxisPoint]] = ops.map(_._2.toSet)

    currentAxis.flatMap {
      axis =>
        val definedValues = toTest.flatMap(_.filter(_.axis == axis).map(_.value))
        val diff = allAxis.get(axis).map(_.diff(definedValues)).toSeq.flatten
        if (diff.nonEmpty) {
          // TODO: quadratic
          val toTestAxises: Set[Set[String]] = toTest.map(_.map(_.axis))
          if (toTestAxises.forall(_.contains(axis))) {
            Some(UnsaturatedAxis(ops.head._1.target, axis, NonEmptySet.unsafeFrom(diff.iterator.map(AxisPoint(axis, _)).toSet)))
          } else None
        } else None
    }
  }
}

object PlanVerifier {
  def apply(preps: GraphPreparations): PlanVerifier = new PlanVerifier(preps)
  def apply(): PlanVerifier = Default

  private[this] object Default extends PlanVerifier(new GraphPreparations(new BindingTranslator.Impl))

  final case class PlanVerifierResult(
    issues: Set[PlanIssue],
    reachableKeys: Set[DIKey],
  )

  sealed abstract class PlanIssue {
    def key: DIKey
  }
  object PlanIssue {
    final case class MissingImport(key: DIKey, dependee: DIKey, origins: Set[(DIKey, OperationOrigin)]) extends PlanIssue {
      override def toString: String = MissingInstanceException.format(key, Set(dependee))
    }

    /** There are reachable axis choices for which there is no binding for this key */
    final case class UnsaturatedAxis(key: DIKey, axis: String, missingAxisValues: NonEmptySet[AxisPoint]) extends PlanIssue

    /** Binding contains multiple axis choices for the same axis */
    final case class ConflictingAxisChoices(key: DIKey, op: InstantiationOp, bad: NonEmptyMap[String, Set[AxisPoint]]) extends PlanIssue

    /** Multiple bindings contain identical axis choices */
    final case class DuplicateActivations(key: DIKey, ops: NonEmptyMap[Set[AxisPoint], NonEmptySet[OperationOrigin]]) extends PlanIssue

    /** There is no possible activation that could choose a unique binding among these contradictory axes */
    final case class UnsolvableConflict(key: DIKey, ops: NonEmptySet[(InstantiationOp, Set[AxisPoint])]) extends PlanIssue

    /** A distage bug, should never happen (bindings machinery guarantees a unique key for each set member, they cannot have the same key by construction) */
    final case class InconsistentSetMembers(key: DIKey, ops: NonEmptyList[InstantiationOp]) extends PlanIssue

    //
//    final case class IncompatibleEffectType(key: DIKey, op: MonadicOp, provisionerEffectType: SafeType, actionEffectType: SafeType) extends PlanIssue
    //
  }
}
