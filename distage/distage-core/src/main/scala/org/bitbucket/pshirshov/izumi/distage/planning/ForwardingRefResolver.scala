package org.bitbucket.pshirshov.izumi.distage.planning

import org.bitbucket.pshirshov.izumi.distage.model.plan.DodgyPlan

trait ForwardingRefResolver {
  def resolve(steps: DodgyPlan): DodgyPlan
}