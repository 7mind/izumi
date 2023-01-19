package izumi.distage.model

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan.*

/** Transforms [[izumi.distage.model.definition.ModuleDef]] into [[izumi.distage.model.plan.Plan]] */
trait Planner {
  def plan(input: PlannerInput): Either[List[DIError], Plan]

  /**
    * Does the same job as [[plan()]] but does not apply any binding rewrites (autocloseables, resources, etc)
    * Most likely you won't need to call this method directly.
    */
  def planNoRewrite(input: PlannerInput): Either[List[DIError], Plan]

  def rewrite(bindings: ModuleBase): ModuleBase

  @inline final def plan(bindings: ModuleBase, activation: Activation, roots: Roots): Either[List[DIError], Plan] =
    plan(PlannerInput(bindings, activation, roots))

  @inline final def plan(bindings: ModuleBase, roots: Roots): Either[List[DIError], Plan] =
    plan(bindings, Activation.empty, roots)

  @deprecated("Use .plan(input).getOrThrow", "1.1.0")
  final def planUnsafe(input: PlannerInput): Plan = {
    plan(input).getOrThrow()
  }

  @deprecated("Use .planNoRewrite(input).getOrThrow", "1.1.0")
  final def planNoRewriteUnsafe(input: PlannerInput): Plan = {
    planNoRewrite(input).getOrThrow()
  }

  @deprecated("Use .plan(bindings, activation, roots).getOrThrow", "1.1.0")
  @inline final def planUnsafe(bindings: ModuleBase, activation: Activation, roots: Roots): Plan = {
    planUnsafe(PlannerInput(bindings, activation, roots))
  }

  @deprecated("Use .plan(bindings, roots).getOrThrow", "1.1.0")
  @inline final def planUnsafe(bindings: ModuleBase, roots: Roots): Plan = {
    planUnsafe(PlannerInput(bindings, Activation.empty, roots))
  }
}
