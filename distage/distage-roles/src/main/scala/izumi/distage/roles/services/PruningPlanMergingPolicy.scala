package izumi.distage.roles.services

import distage.DIKey
import izumi.distage.model.definition.BindingTag
import izumi.distage.model.plan.{DodgyPlan, ExecutableOp, SemiPlan}
import izumi.distage.model.planning.PlanMergingPolicy.DIKeyConflictResolution
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.planning.PlanMergingPolicyDefaultImpl
import izumi.distage.planning.gc.TracingDIGC
import izumi.distage.roles.model.AppActivation
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

class PruningPlanMergingPolicy(
                                logger: IzLogger,
                                activation: AppActivation,
                              ) extends PlanMergingPolicyDefaultImpl {
  private val activeTags = activation.active.values.toSet

  override protected def resolveConflict(plan: DodgyPlan, key: RuntimeDIUniverse.DIKey, operations: Set[DodgyPlan.JustOp]): DIKeyConflictResolution = {
    assert(operations.size > 1)
    val filtered = operations.filter {
      _.binding.tags
        .collect { case BindingTag.AxisTag(t) => t }
        .forall(t => activeTags.contains(t))
    }

    val (explicitlyEnabled, noTags) = filtered.partition(_.binding.tags.nonEmpty)

    if (explicitlyEnabled.size == 1) {
      if (noTags.nonEmpty) {
        logger.info(s"Untagged conflicts were filtered out in $key: ${noTags.niceList() -> "filtered conflicts"}")
        logger.warn(s"Untagged alternatives for conflict in $key were filtered out, continuing...")
      }
      DIKeyConflictResolution.Successful(explicitlyEnabled.map(_.op: ExecutableOp))
    } else if (noTags.size == 1) {
      DIKeyConflictResolution.Successful(noTags.map(_.op: ExecutableOp))
    } else if (filtered.nonEmpty) {
      val hints = makeHints(filtered)
      DIKeyConflictResolution.Failed(operations.map(_.op), s"${filtered.size} options left, possible disambiguations: ${hints.niceList()}")
    } else {
      val hints = makeHints(operations)
      DIKeyConflictResolution.Failed(operations.map(_.op), s"All options were filtered out, original candidates: ${hints.niceList()}")
    }
  }

  override protected def handleIssues(plan: DodgyPlan, resolved: Map[DIKey, Set[ExecutableOp]], issues: Map[DIKey, DIKeyConflictResolution.Failed]): SemiPlan = {
    logger.debug(s"Not enough data to solve conflicts, will try to prune: ${formatIssues(issues) -> "issues"}")

    val ops = resolved.values.flatten.toVector
    val index = ops.map(op => op.target -> op).toMap
    val roots = plan.gcMode.toSet

    if (roots.nonEmpty && roots.intersect(issues.keySet).isEmpty) {
      val collected = new TracingDIGC(roots, index, ignoreMissingDeps = true).gc(ops)

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

      val failed = lastTry.collect({
        case (k, f: DIKeyConflictResolution.Failed) => k -> f
      })

      if (failed.nonEmpty) {
        throwOnIssues(failed)
      } else {
        val good = lastTry.collect({
          case (k, DIKeyConflictResolution.Successful(s)) => k -> s
        })
        val erased = good.filter(_._2.isEmpty)
        logger.info(s"${erased.keys.niceList() -> "erased conflicts"}")
        logger.warn(s"Pruning strategy successfully resolved ${issues.size -> "conlicts"}, ${erased.size -> "erased"}, continuing...")
        val allResolved = (resolved.values.flatten ++ good.values.flatten).toVector
        SemiPlan(plan.definition, allResolved, plan.gcMode)
      }
    } else {
      throwOnIssues(issues)
    }
  }

  private def makeHints(ops: Set[DodgyPlan.JustOp]): Seq[String] = {
    ops
      .toSeq
      .map {
        op =>
          val bindingTags = op.binding.tags.collect({
            case BindingTag.AxisTag(t) => t
          }).diff(activeTags)
          val alreadyActiveTags = op.binding.tags.collect({
            case BindingTag.AxisTag(t) => t
          }).intersect(activeTags)
          s"${op.binding.origin}, possible: {${bindingTags.mkString(", ")}}, active: {${alreadyActiveTags.mkString(", ")}}"
      }
  }
}
