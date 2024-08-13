package izumi.distage.model

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.{Activation, LocatorPrivacy, ModuleBase}
import izumi.distage.model.plan.*
import izumi.fundamentals.collections.nonempty.NEList

/** Transforms [[izumi.distage.model.definition.ModuleDef]] into [[izumi.distage.model.plan.Plan]] */
trait Planner {
  def plan(input: PlannerInput): Either[NEList[DIError], Plan]

  @inline final def plan(
    bindings: ModuleBase,
    roots: Roots,
    activation: Activation = Activation.empty,
    locatorPrivacy: LocatorPrivacy = LocatorPrivacy.PublicByDefault,
  ): Either[NEList[DIError], Plan] = {
    plan(PlannerInput(bindings, roots, activation, locatorPrivacy))
  }

  /**
    * Does the same job as [[plan()]] but does not apply any binding rewrites (autocloseables, resources, etc)
    * Most likely you won't need to call this method directly.
    */
  def planNoRewrite(input: PlannerInput): Either[NEList[DIError], Plan]

  def rewrite(bindings: ModuleBase): ModuleBase

  /** @deprecated Use `.plan(input).getOrThrow()` */
  final def planUnsafe(input: PlannerInput): Plan = {
    plan(input).getOrThrow()
  }
}
