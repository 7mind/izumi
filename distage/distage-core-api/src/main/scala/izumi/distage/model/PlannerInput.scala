package izumi.distage.model

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.Roots
import izumi.distage.model.reflection.DIKey
import izumi.reflect.Tag

/**
  * Input for [[Planner]]
  *
  * @param bindings Bindings. Can be created using [[izumi.distage.model.definition.ModuleDef]] DSL
  *
  * @param roots    Garbage collection [[Roots roots]]. distage will ignore all bindings that aren't transitive dependencies
  *                 of the chosen Root [[DIKey keys]] from the plan - they will never be instantiated.
  *
  *                 Effectively, the choice of roots selects a *sub-graph* of the largest possible object graph
  *                 that can be described by `bindings` - the sub-graph that only includes components designated as `roots`
  *                 and their transitive dependencies.
  *
  *                 On [[Roots.Everything]] garbage collection will not be performed – that would be equivalent to
  *                 designating _all_ DIKeys as roots.
  */
final case class PlannerInput(
  bindings: ModuleBase,
  roots: Roots,
)

object PlannerInput {
  /**
    * Instantiate `roots` and the dependencies of `roots`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph
    * that can be described by `bindings`
    */
  def apply(bindings: ModuleBase, roots: Set[_ <: DIKey]): PlannerInput = PlannerInput(bindings, Roots(roots))

  /** Instantiate `root`, `roots` and their dependencies, discarding bindings that are unrelated. */
  def apply(bindings: ModuleBase, root: DIKey, roots: DIKey*): PlannerInput = PlannerInput(bindings, Roots(root, roots: _*))

  /** Instantiate `T` and the dependencies of `T`, discarding bindings that are unrelated. */
  def target[T: Tag](bindings: ModuleBase): PlannerInput = PlannerInput(bindings, DIKey.get[T])

  /** Instantiate `T @Id(name)` and the dependencies of `T @Id(name)`, discarding bindings that are unrelated.
    *
    * @see [[izumi.distage.model.definition.Id @Id annotation]] */
  def target[T: Tag](name: String)(bindings: ModuleBase): PlannerInput = PlannerInput(bindings, DIKey.get[T].named(name))

  /** Disable all pruning.
    * Every binding in `bindings` will be instantiated eagerly, without selection */
  def noGc(bindings: ModuleBase): PlannerInput = PlannerInput(bindings, Roots.Everything)
}
