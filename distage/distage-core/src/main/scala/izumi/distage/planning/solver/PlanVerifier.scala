package izumi.distage.planning.solver

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.exceptions.PlanVerificationException
import izumi.distage.model.plan.ExecutableOp.{InstantiationOp, MonadicOp}
import izumi.distage.model.plan.{ExecutableOp, Roots}
import izumi.distage.model.planning.PlanIssue.*
import izumi.distage.model.planning.{AxisPoint, PlanIssue}
import izumi.distage.model.reflection.DIKey.SetElementKey
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.distage.planning.{BindingTranslator, SubcontextHandler}
import izumi.fundamentals.collections.MutableMultiMap
import izumi.fundamentals.collections.nonempty.{NEList, NEMap, NESet}
import izumi.fundamentals.platform.strings.IzString.toRichIterable
import izumi.reflect.TagK

import scala.annotation.{nowarn, tailrec}
import scala.concurrent.duration.FiniteDuration

/** @see [[izumi.distage.model.Injector.assert]] */
@nowarn("msg=Unused import")
class PlanVerifier(
  queries: GraphQueries
) {
  import scala.collection.compat.*

  def verify[F[_]: TagK](
    bindings: ModuleBase,
    roots: Roots,
    providedKeys: DIKey => Boolean,
    excludedActivations: Set[NESet[AxisPoint]],
  ): PlanVerifierResult = {
    val verificationHandler = new SubcontextHandler.VerificationHandler(this, excludedActivations)
    val traversal = new GenericSemigraphTraverse(queries, verificationHandler) {
      protected def checkConflicts(
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
          queries.nextDepsToVisit(execOpIndex, withoutCurrentActivations)
        }
      }

      protected def verifyStep(
        currentActivation: Set[AxisPoint],
        providedKeys: DIKey => Boolean,
        key: DIKey,
        dependee: DIKey,
        reportMissing: (DIKey, DIKey) => Left[NEList[MissingImport], Nothing],
        ops: Set[(InstantiationOp, Set[AxisPoint])],
        opsWithMergedSets: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
      ): Either[NEList[PlanIssue], Unit] = {
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
      }
    }
    traversal
      .traverse[F](bindings, roots, providedKeys, excludedActivations).left.map {
        failure =>
          val issues = failure.issues.map(f => PlanIssue.CantVerifyLocalContext(f)).toSet[PlanIssue]
          PlanVerifierResult.Incorrect(Some(NESet.unsafeFrom(issues)), Set.empty, failure.time)
      }.map {
        result =>
          result.maybeIssues match {
            case issues @ Some(_) => PlanVerifierResult.Incorrect(issues, result.visitedKeys, result.time)
            case None => PlanVerifierResult.Correct(result.visitedKeys, result.time)
          }
      }.merge
  }

  def traceReachables[F[_]: TagK](
    bindings: ModuleBase,
    roots: Roots,
    providedKeys: DIKey => Boolean,
    excludedActivations: Set[NESet[AxisPoint]],
  ): Set[DIKey] = {
    val tracingHandler = new SubcontextHandler.TracingHandler()
    val traversal = new GenericSemigraphTraverse(queries, tracingHandler) {
      protected def checkConflicts(
        allAxis: Map[String, Set[String]],
        withoutCurrentActivations: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
        execOpIndex: MutableMultiMap[DIKey, InstantiationOp],
        excludedActivations: Set[NESet[AxisPoint]],
        effectType: SafeType,
      ): Either[NEList[PlanIssue], Seq[(Set[AxisPoint], Set[DIKey])]] = {
        queries.nextDepsToVisit(execOpIndex, withoutCurrentActivations)
      }

      protected def verifyStep(
        currentActivation: Set[AxisPoint],
        providedKeys: DIKey => Boolean,
        key: DIKey,
        dependee: DIKey,
        reportMissing: (DIKey, DIKey) => Left[NEList[MissingImport], Nothing],
        ops: Set[(InstantiationOp, Set[AxisPoint])],
        opsWithMergedSets: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
      ): Either[NEList[PlanIssue], Unit] = {
        Right(())
      }
    }

    traversal
      .traverse[F](bindings, roots, providedKeys, excludedActivations).map {
        result =>
          result.visitedKeys
      }.toOption.get // traverse can't fail
  }

  protected def checkConflicts(
    allAxis: Map[String, Set[String]],
    withoutCurrentActivations: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
    execOpIndex: MutableMultiMap[DIKey, InstantiationOp],
    excludedActivations: Set[NESet[AxisPoint]],
    effectType: SafeType,
    ignoreIssues: Boolean,
  ): Either[NEList[PlanIssue], Seq[(Set[AxisPoint], Set[DIKey])]] = {
    val issues = if (ignoreIssues) {
      List.empty
    } else {
      checkForUnsaturatedAxis(allAxis, withoutCurrentActivations, excludedActivations) ++
      checkForShadowedActivations(allAxis, withoutCurrentActivations) ++
      checkForConflictingAxisChoices(withoutCurrentActivations) ++
      checkForDuplicateActivations(withoutCurrentActivations) ++
      checkForUnsolvableConflicts(withoutCurrentActivations) ++
      checkForIncompatibleEffectType(effectType, withoutCurrentActivations)
    }

    if (issues.nonEmpty) {
      Left(NEList.unsafeFrom(issues))
    } else {
      queries.nextDepsToVisit(execOpIndex, withoutCurrentActivations)
    }
  }

  protected final def checkForConflictingAxisChoices(
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
  protected final def checkForDuplicateActivations(
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
  @tailrec protected final def checkForUnsolvableConflicts(
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
  protected final def checkForUnsaturatedAxis(
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
        if (unsaturatedChoices.nonEmpty && !queries.isIgnoredActivation(excludedActivations, unsaturatedChoices)) {
          // TODO: quadratic
          if (opAxisSets.forall(_ contains currentAxis)) {
            Some(UnsaturatedAxis(withoutSetMembers.head._1.target, currentAxis, NESet.unsafeFrom(unsaturatedChoices)))
          } else None
        } else None
    }
  }

  protected final def checkForShadowedActivations(
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

  protected def checkForIncompatibleEffectType(
    effectType: SafeType,
    ops: Set[(InstantiationOp, Set[AxisPoint], Set[AxisPoint])],
  ): List[IncompatibleEffectType] = {
    ops.iterator.collect {
      case (op: MonadicOp, _, _) if op.effectHKTypeCtor != SafeType.identityEffectType && !(op.effectHKTypeCtor <:< effectType) =>
        IncompatibleEffectType(op.target, op, effectType, op.effectHKTypeCtor)
    }.toList
  }
}

object PlanVerifier {
  def apply(): PlanVerifier = Default
  def apply(preps: GraphQueries): PlanVerifier = new PlanVerifier(preps)

  private object Default extends PlanVerifier(new GraphQueries(new BindingTranslator.Impl))

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
