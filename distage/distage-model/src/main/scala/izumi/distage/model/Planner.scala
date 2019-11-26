package izumi.distage.model

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan._
import izumi.distage.model.plan.initial.PrePlan


/** Transforms [[ModuleBase]] into [[OrderedPlan]] */
trait Planner extends PlannerExtendedAPI {
  def plan(input: PlannerInput): OrderedPlan

  final def plan(input: ModuleBase, gcMode: GCMode): OrderedPlan = {
    plan(PlannerInput(input, gcMode))
  }

  // plan lifecycle
  def planNoRewrite(input: PlannerInput): OrderedPlan

  def rewrite(module: ModuleBase): ModuleBase

  def prepare(input: PlannerInput): PrePlan

  def freeze(plan: PrePlan): SemiPlan

  def finish(semiPlan: SemiPlan): OrderedPlan
}



