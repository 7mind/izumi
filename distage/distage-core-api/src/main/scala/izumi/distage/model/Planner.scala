package izumi.distage.model

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan._

/** Transforms [[izumi.distage.model.definition.ModuleDef]] into [[izumi.distage.model.plan.Plan]] */
trait Planner {
  def plan(input: PlannerInput): Either[List[DIError], Plan]
  def planNoRewrite(input: PlannerInput): Either[List[DIError], Plan]

  def rewrite(bindings: ModuleBase): ModuleBase

  @inline final def plan(bindings: ModuleBase, activation: Activation, roots: Roots): Either[List[DIError], Plan] =
    plan(PlannerInput(bindings, activation, roots))

  @inline final def plan(bindings: ModuleBase, roots: Roots): Either[List[DIError], Plan] =
    plan(bindings, Activation.empty, roots)

  def planUnsafe(input: PlannerInput): Plan

  def planNoRewriteUnsafe(input: PlannerInput): Plan

  @inline final def planUnsafe(bindings: ModuleBase, activation: Activation, roots: Roots): Plan =
    planUnsafe(PlannerInput(bindings, activation, roots))

  @inline final def planUnsafe(bindings: ModuleBase, roots: Roots): Plan =
    planUnsafe(bindings, Activation.empty, roots)
}
