package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, AbstractModuleDef}
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

class PlanningHookDefaultImpl extends PlanningHook {
  def hookStep(context: AbstractModuleDef, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan = next
}

