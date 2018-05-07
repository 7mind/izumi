package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.plan.{ResolvedCyclesPlan, ResolvedSetsPlan}

trait ForwardingRefResolver {
  def resolve(plan: ResolvedSetsPlan): ResolvedCyclesPlan
}
