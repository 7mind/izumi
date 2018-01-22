package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ContextDefinition}
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyPlan

trait PlanningHook {
  def hookStep(context: ContextDefinition, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan

}




