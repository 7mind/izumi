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

  def verify(
    bindings: ModuleBase,
    roots: Roots,
    providedKeys: DIKey => Boolean = _ == DIKey[LocatorRef],
    ignoredActivations: Set[AxisPoint] = Set.empty,
  ): PlanVerifierResult = {
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

    val matrixToTrace: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])] = defns.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap
    val justMutators: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])] = mutators.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap

    val rootKeys: Set[DIKey] = preps.getRoots(roots, justOps)
    val weakSetMembers: Set[WeakEdge[DIKey]] = preps.findWeakSetMembers(setOps, matrix, rootKeys)

    val mutVisited: mutable.HashSet[DIKey] = mutable.HashSet.empty[DIKey]
    val issues = trace(allAxis, mutVisited, matrixToTrace, weakSetMembers, justMutators, providedKeys, ignoredActivations, rootKeys)

    PlanVerifierResult(issues, mutVisited.toSet)
  }

  protected[this] def trace(
    allAxis: Map[String, Set[String]],
    allVisited: mutable.HashSet[DIKey],
    matrix: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    weakSetMembers: Set[WeakEdge[DIKey]],
    justMutators: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    providedKeys: DIKey => Boolean,
    ignoredActivations: Set[AxisPoint],
    rootKeys: Set[DIKey],
  ): Set[PlanIssue] = {

    // for trampoline
    sealed trait RecResult {
      type RecursionResult <: Iterator[Either[List[PlanIssue], Iterator[() => RecursionResult]]]
    }
    type RecursionResult = RecResult#RecursionResult
    @inline def RecursionResult(a: Iterator[Either[List[PlanIssue], Iterator[() => RecursionResult]]]): RecursionResult = a.asInstanceOf[RecursionResult]

    @inline def go(visited: Set[DIKey], current: Set[(DIKey, DIKey)], currentActivation: Set[AxisPoint]): RecursionResult = RecursionResult(current.iterator.map {
      case (key, dependee) =>
        @inline def reportMissing[A](key: DIKey, dependee: DIKey): Left[List[MissingImport], Nothing] = {
          Left(List(MissingImport(key, dependee, allImportingBindings(matrix, currentActivation)(key, dependee))))
        }

        @inline def reportMissingIfNotProvided[A](key: DIKey, dependee: DIKey)(orElse: => Either[List[PlanIssue], A]): Either[List[PlanIssue], A] = {
          if (providedKeys(key)) orElse else reportMissing(key, dependee)
        }

        def tryReportUnsaturated(l: Iterable[UnsaturatedAxis]): Either[List[UnsaturatedAxis], Unit] = {
          val nl = l.iterator.filterNot(_.missingAxisValues.subsetOf(ignoredActivations)).toList
          if (nl.isEmpty) Right(()) else Left(nl)
        }

        if (visited.contains(key)) {
          Right(Iterator.empty)
        } else {
          matrix.get(key) match {
            case None =>
              reportMissingIfNotProvided(key, dependee)(Right(Iterator.empty))

            case Some(ops) =>
              val ac = ActivationChoices(currentActivation)

              val withoutCurrentActivations = {
                val withoutImpossibleActivationsIter = ops.iterator.filter(ac allValid _._2)
                withoutImpossibleActivationsIter.map {
                  case (op, activations) =>
                    (op, activations diff currentActivation, activations)
                }.toSet
              }

              for {
                // we ignore activations for set definitions
                withMergedSets <- {
                  val (setOps, otherOps) = withoutCurrentActivations.partitionMap { case (s: CreateSet, _, _) => Left(s); case a => Right(a) }
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
                                    Left(List(InconsistentSetMembers(memberKey, NonEmptyList.unsafeFrom(value.iterator.map(_._1.origin.value).toList))))
                                  case None =>
                                    reportMissingIfNotProvided(memberKey, key)(Right(List(memberKey)))
                                }
                            }(Set)
                        } yield (ops.head.copy(members = members), Set.empty[AxisPoint], Set.empty[AxisPoint])
                    }
                  } yield otherOps ++ mergedSets
                }
                _ <-
                  if (withMergedSets.isEmpty && !providedKeys(key)) { // provided key cannot have unsaturated axis
                    val allDefinedPoints = ops.flatMap(_._2).groupBy(_.axis)
                    val probablyUnsaturatedAxis = allDefinedPoints.flatMap {
                      case (axis, definedPoints) =>
                        NonEmptySet
                          .from(currentActivation.filter(_.axis == axis).diff(definedPoints))
                          .map(UnsaturatedAxis(key, axis, _))
                    }

                    if (probablyUnsaturatedAxis.isEmpty) {
                      reportMissing(key, dependee)
                    } else {
                      tryReportUnsaturated(probablyUnsaturatedAxis)
                    }
                  } else {
                    Right(())
                  }
                next <- checkConflicts(allAxis, withMergedSets, weakSetMembers, ignoredActivations)
              } yield {
                allVisited.add(key)

                val mutators = justMutators.getOrElse(key, Set.empty).iterator.filter(ac allValid _._2).flatMap(m => depsOf(weakSetMembers)(m._1)).toSeq

                val goNext = next.iterator.map {
                  case (nextActivation, nextDeps) =>
                    () =>
                      go(
                        visited = visited + key,
                        current = (nextDeps ++ mutators).map((_, key)),
                        currentActivation = currentActivation ++ nextActivation,
                      )
                }

                goNext
              }
          }
        }
    })

    // trampoline
    val errors = Set.newBuilder[PlanIssue]
    val remainder = mutable.Stack(() => go(Set.empty, Set.from(rootKeys.map(r => r -> r)), Set.empty))

    while (remainder.nonEmpty) {
      val i = remainder.pop().apply()
      while (i.hasNext) {
        i.next() match {
          case Right(nextSteps) =>
            remainder pushAll nextSteps
          case Left(newErrors) =>
            errors ++= newErrors
        }
      }
    }

    errors.result()
  }

  protected[this] final def allImportingBindings(
    matrix: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    currentActivation: Set[AxisPoint],
  )(importedKey: DIKey,
    d: DIKey,
  ): Set[OperationOrigin] = {
    // FIXME: reuse formatting from conflictingAxisTagsHint
    matrix
      .getOrElse(d, Set.empty)
      .collect {
        case (op, activations) if activations.subsetOf(currentActivation) && (op match {
              case CreateSet(_, members, _) => members
              case op: ExecutableOp.WiringOp => op.wiring.requiredKeys
              case op: ExecutableOp.MonadicOp => Set(op.effectKey)
            }).contains(importedKey) =>
          op.origin.value
      }
  }

  protected[this] def checkConflicts(
    allAxis: Map[String, Set[String]],
    withoutCurrentActivations: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
    weakSetMembers: Set[WeakEdge[DIKey]],
    ignoredActivations: Set[AxisPoint],
  ): Either[List[PlanIssue], Seq[(Set[AxisPoint], Set[DIKey])]] = {
    val issues = {
      checkForUnsaturatedAxis(allAxis, withoutCurrentActivations, ignoredActivations) ++
      checkForShadowedActivations(allAxis, withoutCurrentActivations) ++
      checkForConflictingAxisChoices(withoutCurrentActivations) ++
      checkForDuplicateActivations(withoutCurrentActivations) ++
      checkForUnsolvableConflicts(withoutCurrentActivations)
    }

    if (issues.nonEmpty) {
      Left(issues)
    } else {
      val next = withoutCurrentActivations
        .iterator.map {
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
        }.toSeq
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
            .map(ConflictingAxisChoices(op.target, op.origin.value, _))
      }.toList
  }

  /** this method fails in case any bindings in the set have indistinguishable activations */
  protected[this] final def checkForDuplicateActivations(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[DuplicateActivations] = {
    val duplicateAxisMap = ops
//      .groupBy(_._2)
//      .filter(_._2.sizeIs > 1)
      .groupBy(_._3)
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
          List(UnsolvableConflict(ops.head._1.target, NonEmptySet.unsafeFrom(ops.map(t => t._1.origin.value -> t._3))))
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

  /** This method fails in case there are missing/uncovered points on any of the reachable axis */
  protected[this] final def checkForUnsaturatedAxis(
    allAxis: Map[String, Set[String]],
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
    ignoredActivations: Set[AxisPoint],
  ): List[UnsaturatedAxis] = {
    val currentAxis: List[String] = ops.iterator.flatMap(_._2.iterator.map(_.axis)).toList
    val toTest: Set[Set[AxisPoint]] = ops.map(_._2)

    currentAxis.flatMap {
      axis =>
        val definedValues = toTest.flatMap(_.iterator.filter(_.axis == axis).map(_.value).toSet)
        val diff = allAxis.get(axis).map(_.diff(definedValues)).toSeq.flatten
        if (diff.nonEmpty) {
          // TODO: quadratic
          val toTestAxises: Iterator[Set[String]] = toTest.iterator.map(_.map(_.axis))
          if (toTestAxises.forall(_.contains(axis))) {
            Some(UnsaturatedAxis(ops.head._1.target, axis, NonEmptySet.unsafeFrom(diff.iterator.map(AxisPoint(axis, _)).toSet)))
              .filterNot(_.missingAxisValues.subsetOf(ignoredActivations))
          } else None
        } else None
    }
  }

  protected[this] final def checkForShadowedActivations(
    allAxis: Map[String, Set[String]],
    ops: Set[(ExecutableOp.InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
  ): List[ShadowedActivation] = {
    // FIXME: quadratic shit
    ops
      .iterator.flatMap {
        case (op, _, axis) =>
          val bigger = ops.iterator.collect { case (op, _, thatAxis) if axis != thatAxis && axis.subsetOf(thatAxis) => (thatAxis, op.origin.value) }.toMap
          NonEmptyMap.from(bigger) match {
            case None => Nil
            case Some(strictlyBiggerActivations) =>
              val axisAxes = axis.map(_.axis)
              val coveredAxis = strictlyBiggerActivations.iterator.flatMap(_._1).filterNot(axisAxes contains _.axis).toActivationMultimapMut
              if (coveredAxis == allAxis.view.filterKeys(coveredAxis.keySet).toMap) {
                List(ShadowedActivation(op.target, op.origin.value, axis, allAxis, strictlyBiggerActivations))
              } else {
                Nil
              }
          }
      }.toList
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
    final case class MissingImport(key: DIKey, dependee: DIKey, origins: Set[OperationOrigin]) extends PlanIssue {
      override def toString: String = {
        // FIXME: reuse formatting from conflictingAxisTagsHint [show multiple origins with different axes]
        MissingInstanceException.format(key, Set(dependee))
      }
    }

    /** There are reachable axis choices for which there is no binding for this key */
    final case class UnsaturatedAxis(key: DIKey, axis: String, missingAxisValues: NonEmptySet[AxisPoint]) extends PlanIssue

    /** Binding contains multiple axis choices for the same axis */
    final case class ConflictingAxisChoices(key: DIKey, op: OperationOrigin, bad: NonEmptyMap[String, Set[AxisPoint]]) extends PlanIssue

    /** Multiple bindings contain identical axis choices */
    final case class DuplicateActivations(key: DIKey, ops: NonEmptyMap[Set[AxisPoint], NonEmptySet[OperationOrigin]]) extends PlanIssue

    /** There is a binding with an activation that is completely shadowed by other bindings with larger activations and cannot be chosen */
    final case class ShadowedActivation(
      key: DIKey,
      op: OperationOrigin,
      activation: Set[AxisPoint],
      allPossibleAxisChoices: Map[String, Set[String]],
      shadowingBindings: NonEmptyMap[Set[AxisPoint], OperationOrigin],
    ) extends PlanIssue

    /** There is no possible activation that could choose a unique binding among these contradictory axes */
    final case class UnsolvableConflict(key: DIKey, ops: NonEmptySet[(OperationOrigin, Set[AxisPoint])]) extends PlanIssue

    /** A distage bug, should never happen (bindings machinery guarantees a unique key for each set member, they cannot have the same key by construction) */
    final case class InconsistentSetMembers(key: DIKey, ops: NonEmptyList[OperationOrigin]) extends PlanIssue

    //
//    final case class IncompatibleEffectType(key: DIKey, op: MonadicOp, provisionerEffectType: SafeType, actionEffectType: SafeType) extends PlanIssue
    //
  }
}
