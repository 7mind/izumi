package izumi.distage.model

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan._
import izumi.distage.model.plan.initial.PrePlan
import izumi.distage.model.planning.PlanSplittingOps
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

/** Transforms [[ModuleBase]] into [[OrderedPlan]] */
trait Planner extends PlanSplittingOps {
  def plan(input: PlannerInput): OrderedPlan

  final def plan(bindings: ModuleBase, gcMode: GCMode): OrderedPlan = {
    plan(PlannerInput(bindings, gcMode))
  }

  // plan lifecycle
  def planNoRewrite(input: PlannerInput): OrderedPlan

  def rewrite(bindings: ModuleBase): ModuleBase

  def prepare(input: PlannerInput): PrePlan

  def freeze(plan: PrePlan): SemiPlan

  def finish(semiPlan: SemiPlan): OrderedPlan

  def truncate(plan: OrderedPlan, roots: Set[DIKey]): OrderedPlan
}



