package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy.DIKeyConflictResolution
import com.github.pshirshov.izumi.distage.planning.PlanMergingPolicyDefaultImpl
import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher
import com.github.pshirshov.izumi.distage.roles.model.BackendPluginTags
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.DIKey

abstract class TagFilteringPlanMergingPolicy extends PlanMergingPolicyDefaultImpl {
  protected def allDisabledTags: BindingTag.Expressions.Expr

  override protected def resolveConflict(key: DIKey, operations: Set[DodgyPlan.JustOp]): DIKeyConflictResolution = {
    val filtered = operations.filterNot(op => allDisabledTags.evaluate(op.binding.tags))
    filtered match {
      case s if s.size == 1 =>
        DIKeyConflictResolution.Successful(Set(s.head.op))

      case o =>
        DIKeyConflictResolution.Failed(o.map(_.op))
    }
  }

  protected def filterProductionTags(useDummy: Boolean): BindingTag.Expressions.Composite = {
    if (useDummy) {
      BindingTag.Expressions.all(BackendPluginTags.Production, BackendPluginTags.Storage)
    } else {
      BindingTag.Expressions.any(BackendPluginTags.Test, BackendPluginTags.Dummy)
    }
  }
}

object TagFilteringPlanMergingPolicy {
  @deprecated("DIRTY!", "2019-04-29")
  def log(logger: IzLogger, expr: BindingTag.Expressions.Expr): Unit = {
    logger.trace(s"Raw disabled tags ${expr -> "expression"}")
    logger.info(s"Disabled ${BindingTag.Expressions.TagDNF.toDNF(expr) -> "tags"}")
  }

  def make(expr: BindingTag.Expressions.Expr): TagFilteringPlanMergingPolicy = new TagFilteringPlanMergingPolicy {
    override protected def allDisabledTags: BindingTag.Expressions.Expr = expr
  }
}


@deprecated("We should stop using tags", "2019-04-29")
class RoleAppTagFilteringPlanMergingPolicy(logger: IzLogger, parameters: RawAppArgs, roles: RolesInfo) extends TagFilteringPlanMergingPolicy {

  protected val unrequiredRoleTags: Set[BindingTag.Expressions.Expr] = roles.unrequiredRoleNames.map(v => BindingTag.Expressions.Has(BindingTag(v)): BindingTag.Expressions.Expr)

  protected val disabledTags: BindingTag.Expressions.Expr = parameters.globalParameters.flags
    .find(f => RoleAppLauncher.Options.useDummies.name.matches(f.name))
    .map(_ => filterProductionTags(true))
    .getOrElse(filterProductionTags(false))

  override protected val allDisabledTags = BindingTag.Expressions.Or(Set(disabledTags) ++ unrequiredRoleTags)

  // TODO: dirty
  TagFilteringPlanMergingPolicy.log(logger, disabledTags)
}
