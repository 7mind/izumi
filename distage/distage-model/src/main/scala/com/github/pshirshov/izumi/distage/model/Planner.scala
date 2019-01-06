package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan._

case class PlannerInput(bindings: ModuleBase)


trait Planner {
  def plan(input: PlannerInput): OrderedPlan

  def finish(semiPlan: SemiPlan): OrderedPlan

  def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan
}
