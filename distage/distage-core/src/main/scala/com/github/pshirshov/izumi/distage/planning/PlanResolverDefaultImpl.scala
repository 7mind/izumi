package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.exceptions.UntranslatablePlanException
import com.github.pshirshov.izumi.distage.model.plan.{FinalPlan, FinalPlanImmutableImpl, ResolvedCyclesPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanResolver


class PlanResolverDefaultImpl extends PlanResolver {
  override def resolve(plan: ResolvedCyclesPlan, definition: ModuleBase): FinalPlan = {
    val issues = plan.issues

    if (issues.nonEmpty) {
      throw new UntranslatablePlanException(s"Cannot translate untranslatable (with default policy):\n${issues.mkString("\n")}", issues)
    }

    val ops = plan.statements
    FinalPlanImmutableImpl(definition, ops)
  }
}

