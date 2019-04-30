package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.{BindingTag, EnvAxis}
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy.DIKeyConflictResolution
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.planning.PlanMergingPolicyDefaultImpl
import com.github.pshirshov.izumi.distage.roles.model.AppActivation
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.TagFilteringPlanMergingPolicy.Expressions
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.DIKey


@deprecated("We should stop using tags", "2019-04-29")
abstract class TagFilteringPlanMergingPolicy extends PlanMergingPolicyDefaultImpl {
  protected def allDisabledTags: Expressions.Expr

  override protected def resolveConflict(key: DIKey, operations: Set[DodgyPlan.JustOp]): DIKeyConflictResolution = {
    val filtered = operations.filterNot(op => allDisabledTags.evaluate(op.binding.tags))
    filtered match {
      case s if s.size == 1 =>
        DIKeyConflictResolution.Successful(Set(s.head.op))

      case o =>
        DIKeyConflictResolution.Failed(o.map(_.op), s"Tag expression $allDisabledTags didn't leave us just one value")
    }
  }

  protected def filterProductionTags(useDummy: Boolean): Expressions.Composite = {
    if (useDummy) {
      Expressions.any(EnvAxis.Production)
    } else {
      Expressions.any(EnvAxis.Mock)
    }
  }
}

object TagFilteringPlanMergingPolicy {
  object Expressions extends TagExpr.For[BindingTag]
}

@deprecated("We should stop using tags", "2019-04-29")
class RoleAppTagFilteringPlanMergingPolicy(logger: IzLogger, activation: AppActivation, roles: RolesInfo) extends TagFilteringPlanMergingPolicy {
  protected val disabledTags: Expressions.Expr = {
    activation.active.get(EnvAxis) match {
      case Some(EnvAxis.Mock) =>
        filterProductionTags(true)
      case _ =>
        filterProductionTags(false)
    }
  }

  override protected val allDisabledTags = Expressions.Or(Set(disabledTags))


  def log(logger: IzLogger, expr: Expressions.Expr): Unit = {
    logger.trace(s"Raw disabled tags ${expr -> "expression"}")
    logger.info(s"Disabled ${Expressions.TagDNF.toDNF(expr) -> "tags"}")
  }
}

class UniqueActivationPlanMergingPolicy(logger: IzLogger, activation: AppActivation, roles: RolesInfo) extends PlanMergingPolicyDefaultImpl {
  override def handleIssues(issues: Map[DIKey, DIKeyConflictResolution.Failed]): Nothing = super.handleIssues(issues)

  override protected def resolveConflict(key: RuntimeDIUniverse.DIKey, operations: Set[DodgyPlan.JustOp]): DIKeyConflictResolution = {
    super.resolveConflict(key, operations)
  }
}
