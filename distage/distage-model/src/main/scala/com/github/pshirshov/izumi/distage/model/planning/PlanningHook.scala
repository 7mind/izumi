package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, AbstractModuleDef}
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan

trait PlanningHook {
  def hookStep(context: AbstractModuleDef, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan

}
