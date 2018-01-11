package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, ReadyPlan}

trait PlanResolver {
  def resolve(steps: DodgyPlan): ReadyPlan
}
