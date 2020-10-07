package izumi.distage.model

import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan._
import izumi.distage.model.planning.PlanSplittingOps

/** Transforms [[izumi.distage.model.definition.ModuleDef]] into [[izumi.distage.model.plan.OrderedPlan]] */
trait Planner extends PlanSplittingOps {
  def plan(input: PlannerInput): OrderedPlan
  // plan lifecycle
  def planNoRewrite(input: PlannerInput): OrderedPlan
  def rewrite(bindings: ModuleBase): ModuleBase

  final def plan(bindings: ModuleBase, activation: Activation, roots: Roots): OrderedPlan =
    plan(PlannerInput(bindings, activation, roots))

  final def plan(bindings: ModuleBase, roots: Roots): OrderedPlan =
    plan(bindings, Activation.empty, roots)
}
