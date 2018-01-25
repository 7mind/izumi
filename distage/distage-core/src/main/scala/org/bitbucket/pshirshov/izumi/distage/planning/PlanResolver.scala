package org.bitbucket.pshirshov.izumi.distage.planning

import org.bitbucket.pshirshov.izumi.distage.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}

trait PlanResolver {
  def resolve(steps: DodgyPlan, definition: ContextDefinition): FinalPlan
}
