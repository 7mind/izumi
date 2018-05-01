package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleDef
import com.github.pshirshov.izumi.distage.model.exceptions.UntranslatablePlanException
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, FinalPlanImmutableImpl}
import com.github.pshirshov.izumi.distage.model.planning.PlanResolver


class PlanResolverDefaultImpl extends PlanResolver {
  override def resolve(plan: DodgyPlan, definition: AbstractModuleDef): FinalPlan = {
    val issues = plan.issues

    if (issues.nonEmpty) {
      throw new UntranslatablePlanException(s"Cannot translate untranslatable (with default policy; feel free to jerk off at this point): $issues", issues)
    }

    val ops = plan.statements
    FinalPlanImmutableImpl(definition, ops)
  }
}

