package izumi.distage.planning.solver

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.definition.conflicts.{Annotated, Node}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp}
import izumi.distage.model.plan.Roots
import izumi.distage.model.planning.PlanIssue.*
import izumi.distage.model.planning.{ActivationChoices, AxisPoint, PlanIssue}
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.planning.SubcontextHandler
import izumi.distage.planning.solver.GenericSemigraphTraverse.{TraversalFailure, TraversalResult}
import izumi.distage.planning.solver.SemigraphSolver.SemiEdgeSeq
import izumi.distage.provisioning.strategies.ImportStrategyDefaultImpl
import izumi.functional.IzEither.*
import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.collections.nonempty.{NEList, NESet}
import izumi.fundamentals.collections.{ImmutableMultiMap, MutableMultiMap}
import izumi.reflect.TagK

import java.util.concurrent.TimeUnit
import scala.annotation.nowarn
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

object GenericSemigraphTraverse {
  case class TraversalResult(visitedKeys: Set[DIKey], time: FiniteDuration, maybeIssues: Option[NESet[PlanIssue]])
  case class TraversalFailure[Err](time: FiniteDuration, issues: NEList[Err])
}

abstract class GenericSemigraphTraverse[Err](
  queries: GraphQueries,
  subcontextHandler: SubcontextHandler[Err],
) {

  def traverse[F[_]: TagK](
    bindings: ModuleBase,
    roots: Roots,
    providedKeys: DIKey => Boolean,
    excludedActivations: Set[NESet[AxisPoint]],
  ): Either[TraversalFailure[Err], TraversalResult] = {
    val before = System.currentTimeMillis()
    (for {
      ops <- queries.computeOperationsUnsafe(subcontextHandler, bindings).map(_.toSeq)
    } yield {
      val allAxis: Map[String, Set[String]] = ops.flatMap(_._1.axis).groupBy(_.axis).map {
        case (axis, points) =>
          (axis, points.map(_.value).toSet)
      }
      val (mutators, defns) = ops.partition(_._3.isMutator)
      val justOps = defns.map { case (k, op, _) => k -> op }

      val setOps = queries
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
        .toSeq

      val opsMatrix: Seq[(Annotated[DIKey], Node[DIKey, InstantiationOp])] = queries.toDeps(justOps)

      val matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp] = SemiEdgeSeq(opsMatrix ++ setOps)

      val matrixToTrace = defns.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap
      val justMutators = mutators.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap

      val rootKeys: Set[DIKey] = queries.getRoots(roots, justOps)
      val execOpIndex: MutableMultiMap[DIKey, InstantiationOp] = queries.executableOpIndex(matrix)

      val mutVisited = mutable.HashSet.empty[(DIKey, Set[AxisPoint])]
      val effectType = SafeType.getK[F]

      val issues =
        trace(allAxis, mutVisited, matrixToTrace, execOpIndex, justMutators, providedKeys, excludedActivations, rootKeys, effectType, bindings)

      val visitedKeys: Set[DIKey] = mutVisited.map(_._1).toSet
      val after = System.currentTimeMillis()
      val time: FiniteDuration = FiniteDuration(after - before, TimeUnit.MILLISECONDS)

      val maybeIssues: Option[NESet[PlanIssue]] = NESet.from(issues)

      TraversalResult(visitedKeys, time, maybeIssues)
    }).left.map {
      errs => TraversalFailure(FiniteDuration(System.currentTimeMillis() - before, TimeUnit.MILLISECONDS), errs)
    }
  }

  @nowarn("msg=Unused import")
  protected def trace(
    allAxis: Map[String, Set[String]],
    allVisited: mutable.HashSet[(DIKey, Set[AxisPoint])],
    matrix: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    execOpIndex: MutableMultiMap[DIKey, InstantiationOp],
    justMutators: ImmutableMultiMap[DIKey, (InstantiationOp, Set[AxisPoint])],
    providedKeys: DIKey => Boolean,
    excludedActivations: Set[NESet[AxisPoint]],
    rootKeys: Set[DIKey],
    effectType: SafeType,
    bindings: ModuleBase,
  ): Set[PlanIssue] = {
    import scala.collection.compat.*

    @inline def go(visited: Set[DIKey], current: Set[(DIKey, DIKey)], currentActivation: Set[AxisPoint]): RecursionResult = RecursionResult(current.iterator.map {
      case (key, dependee) =>
        if (visited.contains(key) || allVisited.contains((key, currentActivation))) {
          Right(Iterator.empty)
        } else {
          @inline def reportMissing[A](key: DIKey, dependee: DIKey): Left[NEList[MissingImport], Nothing] = {
            val origins = queries.allImportingBindings(matrix, currentActivation)(key, dependee)
            val similarBindings = ImportStrategyDefaultImpl.findSimilarImports(bindings, key)
            Left(NEList(MissingImport(key, dependee, origins, similarBindings.similarSame, similarBindings.similarSub)))
          }

          @inline def reportMissingIfNotProvided[A](key: DIKey, dependee: DIKey)(orElse: => Either[NEList[PlanIssue], A]): Either[NEList[PlanIssue], A] = {
            if (providedKeys(key)) orElse else reportMissing(key, dependee)
          }

          matrix.get(key) match {
            case None =>
              reportMissingIfNotProvided(key, dependee)(Right(Iterator.empty))

            case Some(allOps) =>
              val ops = allOps.filterNot(o => queries.isIgnoredActivation(excludedActivations, o._2))
              val ac = ActivationChoices(currentActivation)

              val withoutCurrentActivations = {
                val withoutImpossibleActivationsIter = ops.iterator.filter(ac `allValid` _._2)
                withoutImpossibleActivationsIter.map {
                  case (op, activations) =>
                    (op, activations diff currentActivation, activations)
                }.toSet
              }

              for {
                // we ignore activations for set definitions
                opsWithMergedSets <- {
                  val (setOps, otherOps) = withoutCurrentActivations.partitionMap {
                    case (s: CreateSet, _, _) => Left(s)
                    case a => Right(a)
                  }
                  for {
                    mergedSets <- setOps.groupBy(_.target).values.biTraverse {
                      ops =>
                        for {
                          members <- ops.iterator
                            .flatMap(_.members)
                            .biFlatTraverse {
                              memberKey =>
                                matrix.get(memberKey) match {
                                  case Some(value) if value.sizeIs == 1 =>
                                    if (ac.allValid(value.head._2)) Right(List(memberKey)) else Right(Nil)
                                  case Some(value) =>
                                    Left(NEList(InconsistentSetMembers(memberKey, NEList.unsafeFrom(value.iterator.map(_._1.origin.value).toList))))
                                  case None =>
                                    reportMissingIfNotProvided(memberKey, key)(Right(List(memberKey)))
                                }
                            }.to(Set)
                        } yield {
                          (ops.head.copy(members = members), Set.empty[AxisPoint], Set.empty[AxisPoint])
                        }
                    }
                  } yield otherOps ++ mergedSets
                }
                _ <-
                  verifyStep(currentActivation, providedKeys, key, dependee, reportMissing, ops, opsWithMergedSets)
                next <- checkConflicts(allAxis, opsWithMergedSets, execOpIndex, excludedActivations, effectType)
              } yield {
                allVisited.add((key, currentActivation))

                val mutators =
                  justMutators.getOrElse(key, Set.empty).iterator.filter(ac `allValid` _._2).flatMap(m => queries.depsOf(execOpIndex, m._1)).toSeq

                val goNext = next.iterator.map {
                  case (nextActivation, nextDeps) =>
                    () =>
                      go(
                        visited = visited + key,
                        current = (nextDeps ++ mutators).map(_ -> key),
                        currentActivation = currentActivation ++ nextActivation,
                      )
                }

                goNext
              }
          }
        }
    })

    // for trampoline
    sealed trait RecResult {
      type RecursionResult <: Iterator[Either[NEList[PlanIssue], Iterator[() => RecursionResult]]]
    }
    type RecursionResult = RecResult#RecursionResult
    @inline def RecursionResult(a: Iterator[Either[NEList[PlanIssue], Iterator[() => RecursionResult]]]): RecursionResult = a.asInstanceOf[RecursionResult]

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

  protected def verifyStep(
    currentActivation: Set[AxisPoint],
    providedKeys: DIKey => Boolean,
    key: DIKey,
    dependee: DIKey,
    reportMissing: (DIKey, DIKey) => Left[NEList[MissingImport], Nothing],
    ops: Set[(InstantiationOp, Set[AxisPoint])],
    opsWithMergedSets: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
  ): Either[NEList[PlanIssue], Unit]

  protected def checkConflicts(
    allAxis: Map[String, Set[String]],
    withoutCurrentActivations: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
    execOpIndex: MutableMultiMap[DIKey, InstantiationOp],
    excludedActivations: Set[NESet[AxisPoint]],
    effectType: SafeType,
  ): Either[NEList[PlanIssue], Seq[(Set[AxisPoint], Set[DIKey])]]

}
