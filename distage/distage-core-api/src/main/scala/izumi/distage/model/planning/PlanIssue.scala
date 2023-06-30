package izumi.distage.model.planning

import izumi.distage.model.definition.Binding
import izumi.distage.model.exceptions.runtime.MissingInstanceException
import izumi.distage.model.plan.ExecutableOp.MonadicOp
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.fundamentals.collections.nonempty.{NonEmptyList, NonEmptyMap, NonEmptySet}
import izumi.fundamentals.platform.IzumiProject

sealed abstract class PlanIssue {
  def key: DIKey
}

object PlanIssue {
  final case class MissingImport(key: DIKey, dependee: DIKey, origins: Set[OperationOrigin], similarSame: Set[Binding], similarSub: Set[Binding]) extends PlanIssue {
    override def toString: String = {
      // FIXME: reuse formatting from conflictingAxisTagsHint [show multiple origins with different axes]
      MissingInstanceException.format(key, Set(dependee), similarSame, similarSub)
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

  /**
    * A config binding (from `distage-extension-config` module) could not be parsed from the reference config using configured binding.
    *
    * @note [[PlanVerifier]] will not detect this issue, but it may be returned by [[izumi.distage.framework.PlanCheck]]
    */
  final case class UnparseableConfigBinding(key: DIKey, op: OperationOrigin, exception: Throwable) extends PlanIssue

  /** A distage bug, should never happen (bindings machinery guarantees a unique key for each set member, they cannot have the same key by construction) */
  final case class InconsistentSetMembers(key: DIKey, ops: NonEmptyList[OperationOrigin]) extends PlanIssue

  final case class IncompatibleEffectType(key: DIKey, op: MonadicOp, provisionerEffectType: SafeType, actionEffectType: SafeType) extends PlanIssue

  implicit class PlanIssueOps(private val issue: PlanIssue) extends AnyVal {
    def render: String = {
      issue match {
        case i: UnsaturatedAxis =>
          s"${i.key}: axis ${i.axis} has no bindings for choices ${i.missingAxisValues.mkString(", ")}"

        case i: ConflictingAxisChoices =>
          val bad = i.bad.toSeq.map { case (a, p) => s"$a -> ${p.mkString(",")}" }
          s"${i.key}: binding has conflicting axis tags ${bad.mkString("; ")} ${i.op.toSourceFilePosition}"

        case i: DuplicateActivations =>
          val bad = i.ops.toSeq.map { case (a, o) => s"${a.mkString(",")} -> ${o.map(_.toSourceFilePosition).mkString(",")}" }
          s"${i.key}: conflicting bindings for identical axis choices in ${bad.mkString("; ")}"
        case i: UnsolvableConflict =>
          val bad = i.ops.toSeq.map { case (o, p) => s"${o.toSourceFilePosition} -> ${p.mkString(",")}" }
          s"${i.key}: it's not possible to disambiguate conflicting bindings using any combination of activations in ${bad.mkString("; ")}"
        case i: InconsistentSetMembers =>
          IzumiProject.bugReportPrompt(s"${i.key}: non-unique keys for set members in ${i.ops.map(_.toSourceFilePosition).mkString(",")}")
        case i: ShadowedActivation =>
          val shadowed = i.shadowingBindings.toSeq.map { case (a, o) => s"${a.mkString(",")} -> ${o.toSourceFilePosition}" }
          val activation = i.activation.mkString(",")
          val allPossibleChoices = i.allPossibleAxisChoices.map { case (a, c) => s"$a -> ${c.mkString(",")}" }
          s"${i.key} binding is completely shadowed by other bindings and will never be used. Shadowed: ${shadowed.mkString("; ")}; activation: $activation; possible choices: ${allPossibleChoices
              .mkString("; ")}; ${i.op.toSourceFilePosition}"
        case i: UnparseableConfigBinding =>
          import izumi.fundamentals.platform.exceptions.IzThrowable.toRichThrowable
          s"${i.key}: cannot parse configuration ${i.op.toSourceFilePosition}: ${i.exception.stackTrace}"
        case i: IncompatibleEffectType =>
          val origin = i.op.origin.value.toSourceFilePosition
          s"${i.key}: injector uses effect ${i.provisionerEffectType} but binding uses incompatible effect ${i.actionEffectType} $origin"
        case i: MissingImport =>
          i.toString
      }
    }
  }
}
