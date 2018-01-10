package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.{DodgyPlan, ReadyPlan}

trait PlanResolver {
  def resolve(steps: DodgyPlan): ReadyPlan
}
