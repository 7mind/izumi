package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.model.plan.FinalPlan

trait ConfigResolver {
  def resolve(plan: FinalPlan): ConfiguredPlan
}

final case class ConfiguredPlan(configured: FinalPlan)
