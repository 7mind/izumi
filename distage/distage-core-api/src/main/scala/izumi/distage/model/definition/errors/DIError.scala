package izumi.distage.model.definition.errors

import izumi.distage.model.definition.Axis.AxisChoice
import izumi.distage.model.definition.BindingTag.AxisTag
import izumi.distage.model.definition.{Activation, Binding}
import izumi.distage.model.definition.conflicts.MutSel
import ConflictResolutionError.{ConflictingAxisChoices, ConflictingDefs, SetAxisProblem, UnconfiguredAxisInMutators, UnsolvedConflicts}
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.InstantiationOp
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.repr.KeyMinimizer
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.DG
import izumi.fundamentals.platform.IzumiProject

sealed trait DIError

object DIError {

  sealed trait PlanningError extends DIError
  object PlanningError {
    final case class BUG_UnexpectedMutatorKey(key: DIKey, index: Int) extends PlanningError
  }
  sealed trait LoopResolutionError extends DIError

  object LoopResolutionError {
    final case class BUG_NotALoopMember(op: ExecutableOp) extends LoopResolutionError
    final case class BUG_UnableToFindLoop(predcessors: Map[DIKey, Set[DIKey]]) extends LoopResolutionError

    //    final case class BUG_BestLoopResolutionIsNotSupported(op: ExecutableOp.SemiplanOp) extends LoopResolutionError
    //    final case class BestLoopResolutionCannotBeProxied(op: InstantiationOp) extends LoopResolutionError
    final case class NoAppropriateResolutionFound(candidates: Vector[DIKey]) extends LoopResolutionError
  }

  final case class ConflictResolutionFailed(error: ConflictResolutionError[DIKey, InstantiationOp]) extends DIError

  sealed trait VerificationError extends DIError

  object VerificationError {
    final case class BUG_PlanIndexIsBroken(badIndex: Map[DIKey, ExecutableOp]) extends VerificationError
    final case class BUG_PlanIndexHasUnrequiredOps(unreferencedInGraph: Set[DIKey]) extends VerificationError
    final case class BUG_PlanMatricesInconsistent(plan: DG[DIKey, ExecutableOp]) extends VerificationError
    final case class BUG_InitWithoutProxy(missingProxies: Set[DIKey]) extends VerificationError
    final case class BUG_ProxyWithoutInit(missingInits: Set[DIKey]) extends VerificationError

    final case class PlanReferencesMissingOperations(missingInOpsIndex: Set[DIKey]) extends VerificationError
    final case class MissingRefException(missing: Set[DIKey], plan: DG[DIKey, ExecutableOp]) extends VerificationError
    final case class MissingRoots(missingRoots: Set[DIKey]) extends VerificationError
  }

  import izumi.fundamentals.platform.strings.IzString.*

  def format(activation: Activation)(e: DIError): String = e match {
    case error: PlanningError =>
      formatError(error)
    case error: LoopResolutionError =>
      formatError(error)
    case ConflictResolutionFailed(error) =>
      formatConflict(activation)(error)
    case error: VerificationError =>
      formatError(error)
  }
  def formatConflict(activation: Activation)(conflictResolutionError: ConflictResolutionError[DIKey, InstantiationOp]): String = {
    conflictResolutionError match {
      case ConflictingAxisChoices(issues) =>
        val printedActivationSelections = issues.map {
          case (axis, choices) => s"axis: `$axis`, selected: {${choices.map(_.value).mkString(", ")}}"
        }
        s"""Multiple axis choices selected for axes, only one choice must be made selected for an axis:
           |
           |${printedActivationSelections.niceList().shift(4)}""".stripMargin

      case ConflictingDefs(defs) =>
        defs
          .map {
            case (k, nodes) =>
              conflictingAxisTagsHint(
                key = k,
                activeChoices = activation.activeChoices.values.toSet,
                ops = nodes.map(_._2.meta.origin.value),
              )
          }.niceList()

      case UnsolvedConflicts(defs) =>
        defs
          .map {
            case (k, axisBinds) =>
              s"""Conflict resolution failed for key:
                 |
                 |   - ${k.asString}
                 |
                 |   Reason: Unsolved conflicts.
                 |
                 |   Candidates left: ${axisBinds.niceList().shift(4)}""".stripMargin
          }.niceList()
      case UnconfiguredAxisInMutators(problems) =>
        val message = problems
          .map {
            e =>
              s"Mutator for ${e.mutator} defined at ${e.pos} with unconfigured axis: ${e.unconfigured.mkString(",")}"
          }.niceList()
        s"Mutators with unconfigured axis: $message"
      case SetAxisProblem(problems) =>
        problems
          .map {
            case u: SetAxisIssue.UnconfiguredSetElementAxis =>
              s"Set element references axis ${u.unconfigured.mkString(",")} with undefined values: set ${u.set}, element ${u.element}"
            case i: SetAxisIssue.InconsistentSetElementAxis =>
              IzumiProject.bugReportPrompt(s"Set ${i.set} has element with multiple axis sets: ${i.element}, unexpected axis sets: ${i.problems}")
          }.niceList()
    }
  }
  protected[this] def conflictingAxisTagsHint(
    key: MutSel[DIKey],
    activeChoices: Set[AxisChoice],
    ops: Set[OperationOrigin],
  ): String = {
    val keyMinimizer = KeyMinimizer(
      ops.flatMap(_.foldPartial[Set[DIKey]](Set.empty, { case b: Binding.ImplBinding => Set(DIKey.TypeKey(b.implementation.implType)) }))
      + key.key,
      colors = false,
    )
    val axisValuesInBindings = ops.iterator.collect { case d: OperationOrigin.Defined => d.binding.tags }.flatten.collect { case AxisTag(t) => t }.toSet
    val alreadyActiveTags = activeChoices.intersect(axisValuesInBindings)
    val candidates = ops.iterator
      .map {
        op =>
          val bindingTags = op.fold(Set.empty[AxisChoice], _.tags.collect { case AxisTag(t) => t })
          val conflicting = axisValuesInBindings.diff(bindingTags)
          val implTypeStr = op.foldPartial("", { case b: Binding.ImplBinding => keyMinimizer.renderType(b.implementation.implType) })
          s"$implTypeStr ${op.toSourceFilePosition} - required: {${bindingTags.mkString(", ")}}, conflicting: {${conflicting.mkString(", ")}}, active: {${alreadyActiveTags
              .mkString(", ")}}"
      }.niceList().shift(4)

    s"""Conflict resolution failed for key:
       |
       |   - ${keyMinimizer.renderKey(key.key)}
       |
       |   Reason: Conflicting definitions available without a disambiguating axis choice.
       |
       |   Candidates left:$candidates""".stripMargin
  }
  def formatError(e: VerificationError): String = e match {
    case VerificationError.BUG_PlanIndexIsBroken(badIndex) =>
      s"BUG: plan index keys are inconsistent with operations: $badIndex"
    case VerificationError.BUG_PlanIndexHasUnrequiredOps(unreferencedInGraph) =>
      s"BUG: plan index has operations not referenced in dependency graph: $unreferencedInGraph"
    case VerificationError.BUG_PlanMatricesInconsistent(plan) =>
      s"BUG: predcessor matrix in the plan is not equal to transposed successor matrix: $plan"
    case VerificationError.BUG_InitWithoutProxy(missingProxies) =>
      s"BUG: Cannot finish the plan, there are missing MakeProxy operations: $missingProxies!"
    case VerificationError.BUG_ProxyWithoutInit(missingInits) =>
      s"BUG: Cannot finish the plan, there are missing InitProxy operations: $missingInits!"
    case VerificationError.PlanReferencesMissingOperations(missingInOpsIndex) =>
      s"Plan graph references missing operations: ${missingInOpsIndex.niceList()}"
    case VerificationError.MissingRefException(missing, _) =>
      s"Plan is broken, there the following keys are declared as dependencies but missing from the graph: ${missing.niceList()}"
    case VerificationError.MissingRoots(roots) =>
      s"There are no operations for the following plan roots: ${roots.niceList()}"
  }
  def formatError(e: LoopResolutionError): String = e match {
    case LoopResolutionError.BUG_NotALoopMember(badOp) =>
      s"BUG: ${badOp.target} is not an operation which expected to be a user of a cycle"

    case LoopResolutionError.BUG_UnableToFindLoop(predcessors) =>
      s"BUG: Failed to break circular dependencies, loop detector failed on matrix $predcessors which is expected to contain a loop"

    case LoopResolutionError.NoAppropriateResolutionFound(candidates) =>
      s"Failed to break circular dependencies, can't find proxyable candidate among ${candidates.mkString(",")}"
  }

  def formatError(e: PlanningError): String = e match {
    case PlanningError.BUG_UnexpectedMutatorKey(k, index) =>
      s"BUG: Unsupported mutator key $k with index $index"
  }
}
