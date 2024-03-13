package izumi.distage.planning.solver

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.definition.conflicts.{Annotated, Node}
import izumi.distage.model.exceptions.PlanVerificationException
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, MonadicOp}
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.{ExecutableOp, Roots}
import izumi.distage.model.planning.PlanIssue.*
import izumi.distage.model.planning.{ActivationChoices, AxisPoint, PlanIssue}
import izumi.distage.model.reflection.DIKey.SetElementKey
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.planning.solver.SemigraphSolver.SemiEdgeSeq
import izumi.distage.planning.{BindingTranslator, SubcontextHandler}
import izumi.distage.provisioning.strategies.ImportStrategyDefaultImpl
import izumi.functional.IzEither.*
import izumi.fundamentals.collections.IzCollections.*
import izumi.fundamentals.collections.nonempty.{NEList, NEMap, NESet}
import izumi.fundamentals.collections.{ImmutableMultiMap, MutableMultiMap}
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.reflect.TagK

import java.util.concurrent.TimeUnit
import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

/** @see [[izumi.distage.model.Injector.assert]] */
@nowarn("msg=Unused import")
class PlanVerifier(
  preps: GraphPreparations
) {
  import scala.collection.compat.*

  def verify[F[_]: TagK](
    bindings: ModuleBase,
    roots: Roots,
    providedKeys: DIKey => Boolean,
    excludedActivations: Set[NESet[AxisPoint]],
  ): PlanVerifierResult = {
    val before = System.currentTimeMillis()
    var after = before

    val verificationHandler = new SubcontextHandler.VerificationHandler(this, excludedActivations)
    (for {
      ops <- preps.computeOperationsUnsafe(verificationHandler, bindings).map(_.toSeq)
    } yield {
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
        .toSeq

      val opsMatrix: Seq[(Annotated[DIKey], Node[DIKey, InstantiationOp])] = preps.toDeps(justOps)

      val matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp] = SemiEdgeSeq(opsMatrix ++ setOps)

      val matrixToTrace = defns.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap
      val justMutators = mutators.map { case (k, op, _) => (k.key, (op, k.axis)) }.toMultimap

      val rootKeys: Set[DIKey] = preps.getRoots(roots, justOps)
      val execOpIndex: MutableMultiMap[DIKey, InstantiationOp] = preps.executableOpIndex(matrix)

      val mutVisited = mutable.HashSet.empty[(DIKey, Set[AxisPoint])]
      val effectType = SafeType.getK[F]

      val issues =
        try {
          trace(allAxis, mutVisited, matrixToTrace, execOpIndex, justMutators, providedKeys, excludedActivations, rootKeys, effectType, bindings)
        } finally {
          after = System.currentTimeMillis()
        }

      val visitedKeys = mutVisited.map(_._1).toSet
      val time = FiniteDuration(after - before, TimeUnit.MILLISECONDS)

      NESet.from(issues) match {
        case issues @ Some(_) => PlanVerifierResult.Incorrect(issues, visitedKeys, time)
        case None => PlanVerifierResult.Correct(visitedKeys, time)
      }
    }) match {
      case Left(errors) =>
        after = System.currentTimeMillis()
        val time = FiniteDuration(after - before, TimeUnit.MILLISECONDS)
        val issues = errors.map(f => PlanIssue.CantVerifyLocalContext(f)).toSet[PlanIssue]
        PlanVerifierResult.Incorrect(Some(NESet.unsafeFrom(issues)), Set.empty, time)
      case Right(value) => value
    }

  }

  protected[this] def trace(
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

    @inline def go(visited: Set[DIKey], current: Set[(DIKey, DIKey)], currentActivation: Set[AxisPoint]): RecursionResult = RecursionResult(current.iterator.map {
      case (key, dependee) =>
        if (visited.contains(key) || allVisited.contains((key, currentActivation))) {
          Right(Iterator.empty)
        } else {
          @inline def reportMissing[A](key: DIKey, dependee: DIKey): Left[NEList[MissingImport], Nothing] = {
            val origins = allImportingBindings(matrix, currentActivation)(key, dependee)
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
              val ops = allOps.filterNot(o => isIgnoredActivation(excludedActivations)(o._2))
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
                  if (opsWithMergedSets.isEmpty && !providedKeys(key)) { // provided key cannot have unsaturated axis
                    val allDefinedPoints = ops.flatMap(_._2).groupBy(_.axis)
                    val probablyUnsaturatedAxis = allDefinedPoints.iterator.flatMap {
                      case (axis, definedPoints) =>
                        NESet
                          .from(currentActivation.filter(_.axis == axis).diff(definedPoints))
                          .map(UnsaturatedAxis(key, axis, _))
                    }.toList

                    if (probablyUnsaturatedAxis.isEmpty) {
                      reportMissing(key, dependee)
                    } else {
                      Left(NEList.unsafeFrom(probablyUnsaturatedAxis))
                    }
                  } else {
                    Right(())
                  }
                next <- checkConflicts(allAxis, opsWithMergedSets, execOpIndex, excludedActivations, effectType)
              } yield {
                allVisited.add((key, currentActivation))

                val mutators = justMutators.getOrElse(key, Set.empty).iterator.filter(ac `allValid` _._2).flatMap(m => depsOf(execOpIndex)(m._1)).toSeq

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
    execOpIndex: MutableMultiMap[DIKey, InstantiationOp],
    excludedActivations: Set[NESet[AxisPoint]],
    effectType: SafeType,
  ): Either[NEList[PlanIssue], Seq[(Set[AxisPoint], Set[DIKey])]] = {
    val issues =
      checkForUnsaturatedAxis(allAxis, withoutCurrentActivations, excludedActivations) ++
      checkForShadowedActivations(allAxis, withoutCurrentActivations) ++
      checkForConflictingAxisChoices(withoutCurrentActivations) ++
      checkForDuplicateActivations(withoutCurrentActivations) ++
      checkForUnsolvableConflicts(withoutCurrentActivations) ++
      checkForIncompatibleEffectType(effectType, withoutCurrentActivations)

    if (issues.nonEmpty) {
      Left(NEList.unsafeFrom(issues))
    } else {
      val next = withoutCurrentActivations.iterator.map {
        case (op, activations, _) =>
          // TODO: I'm not sure if it's "correct" to "activate" all the points together but it simplifies things greatly
          val deps = depsOf(execOpIndex)(op)
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

  protected[this] final def depsOf(
    execOpIndex: MutableMultiMap[DIKey, InstantiationOp]
  )(op: InstantiationOp
  ): Set[DIKey] = {
    op match {
      case cs: CreateSet =>
        // we completely ignore weak members, they don't make any difference in case they are unreachable through other paths
        val members = cs.members.filter {
          case m: SetElementKey =>
            preps.getSetElementWeakEdges(execOpIndex, m).isEmpty
          case _ =>
            true
        }
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
    ops.iterator.flatMap {
      case (op, activation, _) =>
        NEMap
          .from(activation.groupBy(_.axis).filter(_._2.sizeIs > 1))
          .map(ConflictingAxisChoices(op.target, op.origin.value, _))
    }.toList
  }

  /** this method fails in case any bindings in the set have indistinguishable activations */
  protected[this] final def checkForDuplicateActivations(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[DuplicateActivations] = {
    val duplicateAxisMap = ops
      .groupBy(_._3)
      .filter(_._2.sizeIs > 1)
      .view.mapValues(NESet `unsafeFrom` _.map(_._1.origin.value))
      .toMap

    NEMap
      .from(duplicateAxisMap)
      .map(DuplicateActivations(ops.head._1.target, _))
      .toList
  }

  /** this method fails in case any bindings in the set have indistinguishable activations */
  @tailrec protected[this] final def checkForUnsolvableConflicts(
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])]
  ): List[UnsolvableConflict] = {
    // TODO: in case we implement precedence rules the implementation should change
    ops.iterator.map(_._3.map(_.axis)).filter(_.nonEmpty).reduceOption(_ intersect _) match {
      case None => Nil
      case Some(commonAxes) =>
        if (commonAxes.isEmpty) {
          List(UnsolvableConflict(ops.head._1.target, NESet.unsafeFrom(ops.map(t => t._1.origin.value -> t._3))))
        } else {
          checkForUnsolvableConflicts(ops.map { case (op, cutActs, fullActs) => (op, cutActs, fullActs.filterNot(commonAxes contains _.axis)) })
        }
    }
  }

  /** This method fails in case there are missing/uncovered points on any of the reachable axis */
  protected[this] final def checkForUnsaturatedAxis(
    allAxis: Map[String, Set[String]],
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
    excludedActivations: Set[NESet[AxisPoint]],
  ): List[UnsaturatedAxis] = {
    val withoutSetMembers = ops.filterNot(_._1.target.isInstanceOf[SetElementKey])
    val currentAxes: List[String] = withoutSetMembers.iterator.flatMap(_._2.iterator.map(_.axis)).toList
    val opFilteredActivations: Set[Set[AxisPoint]] = withoutSetMembers.map(_._2)
    val opAxisSets: Set[Set[String]] = opFilteredActivations.iterator.map(_.map(_.axis)).toSet

    currentAxes.flatMap {
      currentAxis =>
        val allCurrentAxisChoices: Set[String] = allAxis.getOrElse(currentAxis, Set.empty[String])
        val opsCurrentAxisChoices: Set[String] = opFilteredActivations.flatMap(_.iterator.filter(_.axis == currentAxis).map(_.value))
        val unsaturatedChoices = (allCurrentAxisChoices diff opsCurrentAxisChoices).map(AxisPoint(currentAxis, _))
        if (unsaturatedChoices.nonEmpty && !isIgnoredActivation(excludedActivations)(unsaturatedChoices)) {
          // TODO: quadratic
          if (opAxisSets.forall(_ contains currentAxis)) {
            Some(UnsaturatedAxis(withoutSetMembers.head._1.target, currentAxis, NESet.unsafeFrom(unsaturatedChoices)))
          } else None
        } else None
    }
  }

  protected[this] final def checkForShadowedActivations(
    allAxis: Map[String, Set[String]],
    ops: Set[(ExecutableOp.InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
  ): List[ShadowedActivation] = {
    // FIXME: quadratic
    ops.iterator.flatMap {
      case (op, _, axis) =>
        val bigger = ops.iterator.collect {
          case (op, _, thatAxis) if axis != thatAxis && axis.subsetOf(thatAxis) =>
            (thatAxis, op.origin.value)
        }.toMap

        NEMap.from(bigger) match {
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

  protected[this] def checkForIncompatibleEffectType(
    effectType: SafeType,
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
  ): List[IncompatibleEffectType] = {
    ops.iterator.collect {
      case (op: MonadicOp, _, _) if op.effectHKTypeCtor != SafeType.identityEffectType && !(op.effectHKTypeCtor <:< effectType) =>
        IncompatibleEffectType(op.target, op, effectType, op.effectHKTypeCtor)
    }.toList
  }

  protected[this] def isIgnoredActivation(excludedActivations: Set[NESet[AxisPoint]])(activation: Set[AxisPoint]): Boolean = {
    excludedActivations.exists(_ subsetOf activation)
  }

}

object PlanVerifier {
  def apply(): PlanVerifier = Default
  def apply(preps: GraphPreparations): PlanVerifier = new PlanVerifier(preps)

  private[this] object Default extends PlanVerifier(new GraphPreparations(new BindingTranslator.Impl))

  sealed abstract class PlanVerifierResult {
    def issues: Option[NESet[PlanIssue]]
    def visitedKeys: Set[DIKey]
    def time: FiniteDuration

    final def verificationPassed: Boolean = issues.isEmpty
    final def verificationFailed: Boolean = issues.nonEmpty

    final def throwOnError(): Unit = this match {
      case incorrect: PlanVerifierResult.Incorrect =>
        throw new PlanVerificationException(
          s"""Plan verification failed, issues were:
             |
             |${incorrect.issues.fromNESet.niceList()}
             |
             |Visited keys:
             |
             |${incorrect.visitedKeys.niceList()}
             |""".stripMargin,
          Right(incorrect),
        )
      case _: PlanVerifierResult.Correct => ()
    }
  }
  object PlanVerifierResult {
    final case class Incorrect(issues: Some[NESet[PlanIssue]], visitedKeys: Set[DIKey], time: FiniteDuration) extends PlanVerifierResult
    final case class Correct(visitedKeys: Set[DIKey], time: FiniteDuration) extends PlanVerifierResult { override def issues: None.type = None }
  }
}
