package izumi.distage.planning

import izumi.distage.model.definition.{Activation, BindingTag}
import izumi.distage.model.exceptions.ConflictingDIKeyBindingsException
import izumi.distage.model.plan.ExecutableOp.SemiplanOp
import izumi.distage.model.plan.GCMode.WeaknessPredicate
import izumi.distage.model.plan._
import izumi.distage.model.plan.initial.PrePlan
import izumi.distage.model.planning.PlanMergingPolicy
import izumi.distage.model.planning.PlanMergingPolicy.{DIKeyConflictResolution, WithResolve}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey
import izumi.distage.planning.PruningPlanMergingPolicyDefaultImpl.PlanMergingPolicyDefaultImpl
import izumi.distage.planning.gc.TracingDIGC
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.strings.IzString._

import scala.collection.mutable

class PruningPlanMergingPolicyDefaultImpl
(
  activation: Activation,
) extends PlanMergingPolicyDefaultImpl {

  private[this] val activeChoices = activation.activeChoices.values.toSet

  protected def logUntaggedConflicts(@unused key: DIKey, @unused noTags: Set[PrePlan.JustOp]): Unit = {}
  protected def logHandleIssues(@unused issues: Map[DIKey, DIKeyConflictResolution.Failed]): Unit = {}
  protected def logPruningSuccesfulResolve(@unused issues: Map[DIKey, DIKeyConflictResolution.Failed], @unused erased: Map[DIKey, Set[SemiplanOp]]): Unit = {}

  override protected def resolveConflict(plan: PrePlan, key: RuntimeDIUniverse.DIKey, operations: Set[PrePlan.JustOp]): DIKeyConflictResolution = {
    assert(operations.size > 1)

    val filtered = operations.filter {
      _.binding.tags
        .collect { case BindingTag.AxisTag(t) => t }
        .forall(activeChoices.contains)
    }

    val (explicitlyEnabled, noTags) = filtered.partition(_.binding.tags.nonEmpty)

    if (explicitlyEnabled.size == 1) {
      if (noTags.nonEmpty) {
        logUntaggedConflicts(key, noTags)
      }
      DIKeyConflictResolution.Successful(explicitlyEnabled.map(_.op: SemiplanOp))
    } else if (noTags.size == 1) {
      DIKeyConflictResolution.Successful(noTags.map(_.op: SemiplanOp))
    } else if (filtered.nonEmpty) {
      val hints = makeHints(filtered)
      DIKeyConflictResolution.Failed(operations.map(_.op), s"${filtered.size} options left, possible disambiguations: ${hints.niceList()}")
    } else {
      val hints = makeHints(operations)
      DIKeyConflictResolution.Failed(operations.map(_.op), s"All options were filtered out, original candidates: ${hints.niceList()}")
    }
  }

  override protected def handleIssues(plan: PrePlan, resolved: Map[DIKey, Set[SemiplanOp]], issues: Map[DIKey, DIKeyConflictResolution.Failed]): SemiPlan = {
    logHandleIssues(issues)

    val ops = resolved.values.flatten.toVector
    val index = ops.map(op => op.target -> op).toMap
    val roots = plan.gcMode.toSet

    if (roots.nonEmpty && roots.intersect(issues.keySet).isEmpty) {
      val collected = new TracingDIGC(roots, index, ignoreMissingDeps = true, WeaknessPredicate.empty).gc(ops)

      val lastTry = issues.map {
        case (k, v) =>
          val reachableCandidates = v.candidates.filter(op => collected.reachable.contains(op.target))

          if (reachableCandidates.size == 1) {
            k -> DIKeyConflictResolution.Successful(reachableCandidates)
          } else if (reachableCandidates.isEmpty) {
            k -> DIKeyConflictResolution.Successful(Set.empty)
          } else {
            k -> v
          }
      }

      val failed = lastTry.collect {
        case (k, f: DIKeyConflictResolution.Failed) => k -> f
      }

      if (failed.nonEmpty) {
        throwOnIssues(failed)
      } else {
        val good = lastTry.collect {
          case (k, DIKeyConflictResolution.Successful(s)) => k -> s
        }
        val erased = good.filter(_._2.isEmpty)
        logPruningSuccesfulResolve(issues, erased)
        val allResolved = (resolved.values.flatten ++ good.values.flatten).toVector
        SemiPlan(allResolved, plan.gcMode)
      }
    } else {
      throwOnIssues(issues)
    }
  }

  private[this] def makeHints(ops: Set[PrePlan.JustOp]): Seq[String] = {
    ops.toSeq.map {
      op =>
        val axisValues = op.binding.tags.collect { case BindingTag.AxisTag(t) => t }

        val bindingTags = axisValues.diff(activeChoices)
        val alreadyActiveTags = axisValues.intersect(activeChoices)

        s"${op.binding.origin}, possible: {${bindingTags.mkString(", ")}}, active: {${alreadyActiveTags.mkString(", ")}}"
    }
  }
}

object PruningPlanMergingPolicyDefaultImpl {

  abstract class PlanMergingPolicyDefaultImpl
    extends PlanMergingPolicy
      with WithResolve {

    override def freeze(plan: PrePlan): SemiPlan = {
      val resolved = mutable.HashMap[distage.DIKey, Set[SemiplanOp]]()
      val issues = mutable.HashMap[distage.DIKey, DIKeyConflictResolution.Failed]()

      plan.freeze.foreach {
        case (k, v) =>
          resolve(plan, k, v) match {
            case DIKeyConflictResolution.Successful(op) =>
              resolved.put(k, op)
            case f: DIKeyConflictResolution.Failed =>
              issues.put(k, f)
          }
      }

      if (issues.nonEmpty) {
        handleIssues(plan, resolved.toMap, issues.toMap)
      } else {
        SemiPlan(resolved.values.flatten.toVector, plan.gcMode)
      }
    }

    protected def handleIssues(@unused plan: PrePlan, @unused resolved: Map[DIKey, Set[SemiplanOp]], issues: Map[DIKey, DIKeyConflictResolution.Failed]): SemiPlan = {
      throwOnIssues(issues)
    }

    protected final def throwOnIssues(issues: Map[distage.DIKey, DIKeyConflictResolution.Failed]): Nothing = {
      val issueRepr = formatIssues(issues)

      throw new ConflictingDIKeyBindingsException(
        message =
          s"""There must be exactly one valid binding for each DIKey.
             |
             |You can use named instances: `make[X].named("id")` method and `distage.Id` annotation to disambiguate
             |between multiple instances of the same type.
             |
             |List of problematic bindings: $issueRepr
         """.stripMargin,
        conflicts = issues
      )
    }

    protected final def formatIssues(issues: Map[DIKey, DIKeyConflictResolution.Failed]): String = {
      issues.map {
        case (k, f) =>
          s"""Conflict resolution failed key $k with reason
             |
             |${f.explanation.shift(4)}

             |    Candidates left: ${f.candidates.niceList().shift(4)}""".
            stripMargin
      }.niceList()
    }
  }

}
