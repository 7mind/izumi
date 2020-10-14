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
                  (op, activations diff currentActivation)
              }
            }
            // we ignore activations for set definitions
            mergedSets <- {
              val setOps = withoutDefinedActivations.collect { case (s: CreateSet, _) => s }
              setOps
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
                                Left(List(InconsistentSetMembers(m, value.map(_._1).toSeq)))
                              case None =>
                                Left(List(MissingImport(m, key, allImportingBindings(key))))
                            }
                        }.biAggregate
                    } yield {
                      (o.head.copy(members = members.filter(_._2).map(_._1)), Set.empty[AxisPoint])
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
    withoutDefinedActivations: Set[(InstantiationOp, Set[AxisPoint])],
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
      next = withoutDefinedActivations.toSeq.map {
        case (op, activations) =>
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
    } yield {
      next
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

  def checkForConflictingAxis(
    ops: Set[(InstantiationOp, Set[AxisPoint])]
  ): List[PlanIssue] = {
    ops
      .iterator
      .flatMap {
        case (op, acts) =>
          NonEmptyMap
            .from(acts.groupBy(_.axis).filter(_._2.size > 1))
            .map(ConflictingActivations(op.target, op, _))
      }.toList
  }

  def checkForConflictingBindings(
    ops: Set[(InstantiationOp, Set[AxisPoint])]
  ): List[PlanIssue] = {
    // TODO: this method should fail in case any bindings in the set are indistinguishable
    // TODO: in case we implement precedence rules the implementation should change

    val compatible = mutable.ArrayBuffer.empty[(InstantiationOp, Set[AxisPoint])]
    compatible.appendAll(ops.headOption)

    val incompatible = List.newBuilder[InstantiationOp]

    // TODO: quadratic, better approaches possible
    for ((op, a) <- ops.drop(1)) {
      if (compatible.forall(c => isCompatible(c._2, a))) {
        compatible.append((op, a))
      } else {
        incompatible += op
      }
    }

    NonEmptyList
      .from(incompatible.result())
      .map(ConflictingBindings(ops.head._1.target, _))
      .toList
  }

  def isCompatible(c: Set[AxisPoint], a: Set[AxisPoint]): Boolean = {
    val common = c.intersect(a)
    val cDiff = c.diff(common)
    val aDiff = a.diff(common)
    !(cDiff.isEmpty && aDiff.isEmpty)
  }

  def checkForUnsaturatedAxis(
    allAxis: Map[String, Set[String]],
    ops: Set[(InstantiationOp, Set[AxisPoint])],
  ): List[PlanIssue] = {
    val currentAxis = ops.flatMap(_._2.map(_.axis))
    // TODO: this method should fail in case there are some missing/uncovered points on any of the axis
    val toTest = ops.map(_._2.toSet)
    val issues = mutable.ArrayBuffer.empty[PlanIssue]

    currentAxis.foreach {
      axis =>
        val definedValues = toTest.flatMap(_.filter(_.axis == axis)).map(_.value)
        val diff = allAxis.get(axis).map(_.diff(definedValues)).toSeq.flatten
        if (diff.nonEmpty) {
          // TODO: quadratic
          val toTestAxises = toTest.map(_.map(_.axis))
          if (toTestAxises.forall(_.contains(axis))) {
            issues.append(UnsaturatedAxis(ops.head._1.target, axis, NonEmptySet.unsafeFrom(diff.iterator.map(AxisPoint(axis, _)).toSet)))
          }
        }
    }

    issues.toList
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
    final case class ConflictingBindings(key: DIKey, ops: NonEmptyList[InstantiationOp]) extends PlanIssue
    final case class ConflictingActivations(key: DIKey, op: InstantiationOp, bad: NonEmptyMap[String, Set[AxisPoint]]) extends PlanIssue
    final case class UnsaturatedAxis(key: DIKey, axis: String, missingAxisValues: NonEmptySet[AxisPoint]) extends PlanIssue
    final case class MissingImport(key: DIKey, dependee: DIKey, origins: Set[(DIKey, OperationOrigin)]) extends PlanIssue {
      override def toString: String = MissingInstanceException.format(key, Set(dependee))
    }
    // distage bug, should never happen (bindings machinery must generate a unique key for each set member, they cannot have the same key by construction)
    final case class InconsistentSetMembers(key: DIKey, ops: Seq[InstantiationOp]) extends PlanIssue
    //
//    final case class IncompatibleEffectType(key: DIKey, op: MonadicOp, provisionerEffectType: SafeType, actionEffectType: SafeType) extends PlanIssue
    //
  }
}
