package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyPlan

trait ForwardingRefResolver {
  def resolve(steps: DodgyPlan): DodgyPlan
}