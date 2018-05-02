package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleDef}
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

class PlanningHookDefaultImpl extends PlanningHook {
  def hookStep(context: ModuleDef, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan = next
}

