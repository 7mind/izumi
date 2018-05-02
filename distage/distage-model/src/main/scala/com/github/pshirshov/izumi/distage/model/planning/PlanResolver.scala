package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}

trait PlanResolver {
  def resolve(steps: DodgyPlan, definition: ModuleDef): FinalPlan
}
