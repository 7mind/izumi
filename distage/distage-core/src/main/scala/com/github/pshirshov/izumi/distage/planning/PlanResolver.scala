package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.definition.ContextDefinition
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}

trait PlanResolver {
  def resolve(steps: DodgyPlan, definition: ContextDefinition): FinalPlan
}
