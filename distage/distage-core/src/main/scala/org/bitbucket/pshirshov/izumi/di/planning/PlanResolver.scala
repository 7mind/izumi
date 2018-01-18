package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.ContextDefinition
import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, FinalPlan}

trait PlanResolver {
  def resolve(steps: DodgyPlan, definition: ContextDefinition): FinalPlan
}
