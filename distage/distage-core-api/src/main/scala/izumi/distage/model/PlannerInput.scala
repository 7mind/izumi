package izumi.distage.model

import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan.GCMode
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.reflection.Tags.Tag

/**
  * Input for [[Planner]]
  *
  * @param bindings Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
  * @param mode     Garbage collection roots.
  *
  *                 Garbage collector will remove all bindings that aren't direct or indirect dependencies
  *                 of the chosen root DIKeys from the plan - they will never be instantiated.
  *
  *                 Effectively, garbage collector selects and creates a *sub-graph* of the largest possible object graph
  *                 that can be described by `bindings`, the sub-graph that can includes components designated as `roots`
  *                 and their required dependencies.
  *
  *                 On [[GCMode.NoGC]] garbage collection will not be performed – that would be equivalent to
  *                 designating _all_ DIKeys as roots. _Everything_ described in `bindings` will be instantiated eagerly.
  */
final case class PlannerInput(
  bindings: ModuleBase,
  activation: Activation,
  mode: GCMode,
)

object PlannerInput {
  /**
    * Instantiate `roots` and the dependencies of `roots`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph
    * that can be described by `bindings`
    */
  def apply(bindings: ModuleBase, activation: Activation, roots: Set[_ <: DIKey]): PlannerInput = PlannerInput(bindings, activation, GCMode(roots))

  /** Instantiate `root`, `roots` and their dependencies, discarding bindings that are unrelated. */
  def apply(bindings: ModuleBase, activation: Activation, root: DIKey, roots: DIKey*): PlannerInput = PlannerInput(bindings, activation, GCMode(root, roots: _*))

  /** Instantiate `T` and the dependencies of `T`, discarding bindings that are unrelated. */
  def target[T: Tag](bindings: ModuleBase, activation: Activation): PlannerInput = PlannerInput(bindings, activation, DIKey.get[T])

  /** Instantiate `T @Id(name)` and the dependencies of `T @Id(name)`, discarding bindings that are unrelated.
    *
    * @see [[izumi.distage.model.definition.Id @Id annotation]] */
  def target[T: Tag](name: String)(bindings: ModuleBase, activation: Activation): PlannerInput = PlannerInput(bindings, activation, DIKey.get[T].named(name))

  /** Disable all pruning.
    * Every binding in `bindings` will be instantiated eagerly, without selection */
  def noGc(bindings: ModuleBase, activation: Activation = Activation.empty): PlannerInput = PlannerInput(bindings, activation, GCMode.NoGC)
}
