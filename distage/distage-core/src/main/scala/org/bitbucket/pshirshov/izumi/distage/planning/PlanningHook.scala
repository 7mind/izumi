package org.bitbucket.pshirshov.izumi.distage.planning

import org.bitbucket.pshirshov.izumi.distage.definition.{Binding, ContextDefinition}
import org.bitbucket.pshirshov.izumi.distage.model.plan.DodgyPlan

trait PlanningHook {
  def hookStep(context: ContextDefinition, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan

}




