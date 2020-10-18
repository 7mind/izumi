package izumi.distage.planning.solver

import izumi.distage.model.definition.Axis.AxisPoint
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.definition.conflicts.{Annotated, Node}
import izumi.distage.model.exceptions.MissingInstanceException
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.{ExecutableOp, Roots}
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.BindingTranslator
import izumi.distage.planning.solver.PlanVerifier.PlanIssue._
import izumi.distage.planning.solver.PlanVerifier.{PlanIssue, PlanVerifierResult}
import izumi.distage.planning.solver.SemigraphSolver.SemiEdgeSeq
import izumi.functional.IzEither._
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptyMap, NonEmptySet}
import izumi.fundamentals.graphs.WeakEdge

import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable

@nowarn("msg=Unused import")
class PlanVerifier(
  preps: GraphPreparations
) {
  import scala.collection.compat._

  def verify(bindings: ModuleBase, roots: Roots, providedKeys: DIKey => Boolean = _ == DIKey[LocatorRef]): PlanVerifierResult = {
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
    val justMutators: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])] = mutators.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap

    val rootKeys: Set[DIKey] = preps.getRoots(roots, justOps)
    val weakSetMembers: Set[WeakEdge[DIKey]] = preps.findWeakSetMembers(setOps, matrix, rootKeys)

    val mutVisited: mutable.HashSet[DIKey] = mutable.HashSet.empty[DIKey]
    val issues = trace(allAxis, mutVisited, toTrace, weakSetMembers, justMutators, providedKeys)(rootKeys.map(r => (r, r)), Set.empty)

    PlanVerifierResult(issues.fold(_.toSet, _ => Set.empty[PlanIssue]), mutVisited.toSet)
  }

  protected[this] def trace(
    allAxis: Map[String, Set[String]],
    visited: mutable.HashSet[DIKey],
    matrix: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    weakSetMembers: Set[WeakEdge[DIKey]],
    justMutators: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    providedKeys: DIKey => Boolean,
  )(current: Set[(DIKey, DIKey)],
    currentActivation: Set[AxisPoint],
  ): Either[List[PlanIssue], Unit] = {
    current.biMapAggregateVoid {
      case (key, dependee) =>
        @inline def reportMissing[A](key: DIKey, dependee: DIKey): Left[List[MissingImport], Nothing] = {
          Left(List(MissingImport(key, dependee, allImportingBindings(matrix, currentActivation)(key, dependee))))
        }

        @inline def reportMissingIfNotProvided[A](key: DIKey, dependee: DIKey)(orElse: => Either[List[PlanIssue], A]): Either[List[PlanIssue], A] = {
          if (providedKeys(key)) orElse else reportMissing(key, dependee)
        }

        val currentResult: Either[List[PlanIssue], () => Either[List[PlanIssue], Unit]] = {
          if (visited.contains(key)) {
            Right(() => Right(()))
          } else {
            matrix.get(key) match {
              case None =>
                reportMissingIfNotProvided(key, dependee)(Right(() => Right(())))

              case Some(ops) =>
                val ac = ActivationChoices(currentActivation)

                val withoutDefinedActivations = {
                  val withoutImpossibleActivationsIter = ops.iterator.filter(ac allValid _._2)
                  withoutImpossibleActivationsIter.map {
                    case (op, activations) =>
                      (op, activations diff currentActivation, activations)
                  }.toSet
                }

                for {
                  // we ignore activations for set definitions
                  withMergedSets <- {
                    val (setOps, otherOps) = withoutDefinedActivations.partitionMap { case (s: CreateSet, _, _) => Left(s); case a => Right(a) }
                    for {
                      mergedSets <- setOps.groupBy(_.target).values.biMapAggregate {
                        ops =>
                          for {
                            members <- ops
                              .iterator.flatMap(_.members).biFlatMapAggregateTo {
                                memberKey =>
                                  matrix.get(memberKey) match {
                                    case Some(value) if value.sizeIs == 1 =>
                                      if (ac.allValid(value.head._2)) Right(List(memberKey)) else Right(Nil)
                                    case Some(value) =>
                                      Left(List(InconsistentSetMembers(memberKey, NonEmptyList.unsafeFrom(value.iterator.map(_._1).toList))))
                                    case None =>
                                      reportMissingIfNotProvided(memberKey, key)(Right(List(memberKey)))
                                  }
                              }(Set)
                          } yield (ops.head.copy(members = members), Set.empty[AxisPoint], Set.empty[AxisPoint])
                      }
                    } yield otherOps ++ mergedSets
                  }
                  _ <-
                    // provided key cannot have unsaturated axis
                    if (withMergedSets.isEmpty && !providedKeys(key)) {
                      val defined = ops.flatMap(_._2).groupBy(_.axis)
                      val probablyUnsaturatedAxis = defined.flatMap {
                        case (axis, definedPoints) =>
                          NonEmptySet
                            .from(currentActivation.filter(_.axis == axis).diff(definedPoints))
                            .map(UnsaturatedAxis(key, axis, _))
                      }

                      if (probablyUnsaturatedAxis.isEmpty) {
                        reportMissing(key, dependee)
                      } else {
                        Left(probablyUnsaturatedAxis.toList)
                      }
                    } else {
                      Right(())
                    }
                  next <- checkConflicts(allAxis, withMergedSets, weakSetMembers)
                } yield {
                  val mutators = justMutators.getOrElse(key, Set.empty).iterator.filter(ac allValid _._2).flatMap(m => depsOf(weakSetMembers)(m._1)).toSeq

                  val goNext = () => {
                    next.biMapAggregateVoid {
                      case (nextActivation, nextDeps) =>
                        trace(allAxis, visited, matrix, weakSetMembers, justMutators, providedKeys)(
                          current = (nextDeps ++ mutators).map((_, key)),
                          currentActivation = currentActivation ++ nextActivation,
                        )
                    }
                  }

                  visited.add(key)

                  goNext
                }
            }
          }
        }
        currentResult match {
          case Right(value) => value()
          case l @ Left(_) => l
        }
    }
  }

  protected[this] final def allImportingBindings(
    matrix: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    currentActivation: Set[AxisPoint],
  )(importedKey: DIKey,
    d: DIKey,
  ): Set[(DIKey, OperationOrigin)] = {
    // FIXME: reuse formatting from conflictingAxisTagsHint
    matrix
      .getOrElse(d, Set.empty)
      .collect {
        case (op, activations) if activations.subsetOf(currentActivation) && (op match {
              case CreateSet(_, members, _) => members
              case op: ExecutableOp.WiringOp => op.wiring.requiredKeys
              case op: ExecutableOp.MonadicOp => Set(op.effectKey)
            }).contains(importedKey) =>
          op.target -> op.origin.value
      }
  }

  protected[this] def checkConflicts(
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

  protected[this] final def depsOf(weakSetMembers: Set[WeakEdge[DIKey]])(op: InstantiationOp): Set[DIKey] = {
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

  protected[this] final def checkForConflictingAxisChoices(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[ConflictingAxisChoices] = {
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
  protected[this] final def checkForDuplicateActivations(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[DuplicateActivations] = {
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
  @tailrec protected[this] final def checkForUnsolvableConflicts(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[UnsolvableConflict] = {
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

    ops.iterator.map(_._3.map(_.axis)).filter(_.nonEmpty).reduceOption(_ intersect _) match {
      case None => Nil
      case Some(commonAxes) =>
        if (commonAxes.isEmpty) {
          List(UnsolvableConflict(ops.head._1.target, NonEmptySet.unsafeFrom(ops.map(t => t._1 -> t._3))))
        } else {
          checkForUnsolvableConflicts(ops.map { case (op, cutActs, fullActs) => (op, cutActs, fullActs.filterNot(commonAxes contains _.axis)) })
        }
    }
  }

//  private[this] def isCompatible(a: Set[AxisPoint], b: Set[AxisPoint]): Boolean = {
//    val aAxis = a.map(_.axis)
//    val bAxis = b.map(_.axis)
//    aAxis.intersect(bAxis).nonEmpty || aAxis.isEmpty || bAxis.isEmpty
//  }

  /** This method fails in case there are missing/uncovered points on any of the axis */
  protected[this] final def checkForUnsaturatedAxis(
    allAxis: Map[String, Set[String]],
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
  ): List[UnsaturatedAxis] = {
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
