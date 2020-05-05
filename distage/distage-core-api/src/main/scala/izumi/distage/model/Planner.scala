package izumi.distage.model

import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan._
import izumi.distage.model.plan.initial.PrePlan
import izumi.distage.model.planning.PlanSplittingOps
import izumi.distage.model.reflection._

/** Transforms [[ModuleBase]] into [[OrderedPlan]] */
trait Planner extends PlanSplittingOps {
  def plan(input: PlannerInput): OrderedPlan

  final def plan(bindings: ModuleBase, activation: Activation, gcMode: GCMode): OrderedPlan = {
    plan(PlannerInput(bindings, activation, gcMode))
  }

  // plan lifecycle
  def planNoRewrite(input: PlannerInput): OrderedPlan

  def rewrite(bindings: ModuleBase): ModuleBase

  def prepare(input: PlannerInput): PrePlan

  def freeze(activation: Activation)(plan: PrePlan): SemiPlan

  def finish(semiPlan: SemiPlan): OrderedPlan

  def truncate(plan: OrderedPlan, roots: Set[DIKey]): OrderedPlan
}
