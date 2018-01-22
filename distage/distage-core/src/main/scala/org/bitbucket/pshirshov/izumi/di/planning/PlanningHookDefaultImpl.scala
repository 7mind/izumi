package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ContextDefinition}
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyPlan

class PlanningHookDefaultImpl extends PlanningHook {
  def hookStep(context: ContextDefinition, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan = next
}

object PlanningHookDefaultImpl {
  final val instance = new PlanningHookDefaultImpl()
}