package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.ContextDefinition
import com.github.pshirshov.izumi.distage.model.exceptions.UntranslatablePlanException
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan, FinalPlanImmutableImpl}




class PlanResolverDefaultImpl extends PlanResolver {
  override def resolve(plan: DodgyPlan, definition: ContextDefinition): FinalPlan = {
    val issues = plan.issues

    if (issues.nonEmpty) {
      throw new UntranslatablePlanException(s"Cannot translate untranslatable (with default policy, feel free to jerk at this point): $issues", issues)
    }

    val ops = plan.statements
    new FinalPlanImmutableImpl(ops, definition)
  }
}
