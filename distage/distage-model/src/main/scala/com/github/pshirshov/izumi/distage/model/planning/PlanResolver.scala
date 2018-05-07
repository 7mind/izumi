package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.plan.{FinalPlan, ResolvedCyclesPlan}

trait PlanResolver {
  def resolve(steps: ResolvedCyclesPlan, definition: ModuleDef): FinalPlan
}
