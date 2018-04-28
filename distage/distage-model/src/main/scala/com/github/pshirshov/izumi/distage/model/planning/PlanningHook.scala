package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleDef}
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan

trait PlanningHook {
  def hookStep(context: ModuleDef, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan

}
