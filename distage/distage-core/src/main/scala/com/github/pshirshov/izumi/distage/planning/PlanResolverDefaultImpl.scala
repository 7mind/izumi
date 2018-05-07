package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.exceptions.UntranslatablePlanException
import com.github.pshirshov.izumi.distage.model.plan.{FinalPlan, FinalPlanImmutableImpl, ResolvedCyclesPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanResolver


class PlanResolverDefaultImpl extends PlanResolver {
  override def resolve(plan: ResolvedCyclesPlan, definition: ModuleDef): FinalPlan = {
    val issues = plan.issues

    if (issues.nonEmpty) {
      throw new UntranslatablePlanException(s"Cannot translate untranslatable (with default policy; feel free to jerk off at this point): $issues", issues)
    }

    val ops = plan.statements
    FinalPlanImmutableImpl(definition, ops)
  }
}

