package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ContextDefinition}
import com.github.pshirshov.izumi.distage.model.plan.DodgyPlan
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook

class PlanningHookDefaultImpl extends PlanningHook {
  def hookStep(context: ContextDefinition, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan = next
}

