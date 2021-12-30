package izumi.distage.model

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan._

/** Transforms [[izumi.distage.model.definition.ModuleDef]] into [[izumi.distage.model.plan.Plan]] */
trait Planner {
  def plan(input: PlannerInput): Plan
  def planNoRewrite(input: PlannerInput): Plan

  def planSafe(input: PlannerInput): Either[List[DIError], Plan]
  def planNoRewriteSafe(input: PlannerInput): Either[List[DIError], Plan]

  def rewrite(bindings: ModuleBase): ModuleBase

  @inline final def planSafe(bindings: ModuleBase, activation: Activation, roots: Roots): Either[List[DIError], Plan] =
    planSafe(PlannerInput(bindings, activation, roots))

  @inline final def planSafe(bindings: ModuleBase, roots: Roots): Either[List[DIError], Plan] =
    planSafe(bindings, Activation.empty, roots)

  @inline final def plan(bindings: ModuleBase, activation: Activation, roots: Roots): Plan =
    plan(PlannerInput(bindings, activation, roots))

  @inline final def plan(bindings: ModuleBase, roots: Roots): Plan =
    plan(bindings, Activation.empty, roots)
}
