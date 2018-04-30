package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleDef
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}

trait PlanResolver {
  def resolve(steps: DodgyPlan, definition: AbstractModuleDef): FinalPlan
}
