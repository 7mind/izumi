package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, ExecutableOp}
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy.DIKeyConflictResolution
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.planning.PlanMergingPolicyDefaultImpl
import com.github.pshirshov.izumi.distage.roles.model.AppActivation
import com.github.pshirshov.izumi.logstage.api.IzLogger


class UniqueActivationPlanMergingPolicy(
                                         logger: IzLogger,
                                         activation: AppActivation,
                                       ) extends PlanMergingPolicyDefaultImpl {
  private val activeTags = activation.active.values.toSet

  import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

  override protected def resolveConflict(plan: DodgyPlan, key: RuntimeDIUniverse.DIKey, operations: Set[DodgyPlan.JustOp]): DIKeyConflictResolution = {
    val filtered = operations.filter(_.binding.tags.collect({ case BindingTag.AxisTag(t) => t }).forall(t => activeTags.contains(t)))
    val ops = filtered.map(_.op: ExecutableOp)
    if (filtered.size == 1) {
      DIKeyConflictResolution.Successful(ops)
    } else {
      if (filtered.nonEmpty) {
        val hints = makeHints(filtered)
        DIKeyConflictResolution.Failed(ops, s"${filtered.size} options left, possible disambiguations: ${hints.niceList()}")
      } else {
        val hints = makeHints(operations)
        DIKeyConflictResolution.Failed(ops, s"All options were filtered out, original candidates: ${hints.niceList()}")
      }
    }
  }


  private def makeHints(ops: Set[DodgyPlan.JustOp]): Seq[String] = {
    ops
      .toSeq
      .map {
        f =>
          val bindingTags = f.binding.tags.collect({ case BindingTag.AxisTag(t) => t }).diff(activeTags)
          val alreadyActiveTags = f.binding.tags.collect({ case BindingTag.AxisTag(t) => t }).intersect(activeTags)
          s"${f.binding.origin}, possible: {${bindingTags.mkString(", ")}}, active: {${alreadyActiveTags.mkString(", ")}}"
      }
  }
}
