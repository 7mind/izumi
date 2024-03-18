package izumi.distage.model

import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan.Roots
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.collections.nonempty.NESet
import izumi.reflect.Tag

/**
  * Input for [[Planner]]
  *
  * @param bindings Bindings. Can be created using [[izumi.distage.model.definition.ModuleDef]] DSL
  *
  * @param roots    Garbage collection [[izumi.distage.model.plan.Roots roots]]. distage will ignore all bindings that aren't transitive dependencies
  *                 of the chosen Root [[izumi.distage.model.reflection.DIKey keys]] from the plan - they will never be instantiated.
  *
  *                 Effectively, the choice of roots selects a *sub-graph* of the largest possible object graph
  *                 that can be described by `bindings` - the sub-graph that only includes components designated as `roots`
  *                 and their transitive dependencies.
  *
  *                 On [[izumi.distage.model.plan.Roots.Everything]] garbage collection will not be performed – that would be equivalent to
  *                 designating _all_ DIKeys as roots.
  */
final case class PlannerInput(
  bindings: ModuleBase,
  activation: Activation,
  roots: Roots,
)

object PlannerInput {
  /**
    * Instantiate `roots` and the dependencies of `roots`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    */
  def apply(bindings: ModuleBase, activation: Activation, roots: NESet[? <: DIKey]): PlannerInput = PlannerInput(bindings, activation, Roots(roots))

  /**
    * Instantiate `roots` and the dependencies of `roots`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    */
  def apply(bindings: ModuleBase, activation: Activation, roots: Set[? <: DIKey])(implicit d: DummyImplicit): PlannerInput =
    PlannerInput(bindings, activation, Roots(roots))

  /** Instantiate `root`, `roots` and their dependencies, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    */
  def apply(bindings: ModuleBase, activation: Activation, root: DIKey, roots: DIKey*): PlannerInput = PlannerInput(bindings, activation, Roots(root, roots*))

  /** Instantiate `T` and the dependencies of `T`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    */
  def target[T: Tag](bindings: ModuleBase, activation: Activation): PlannerInput = PlannerInput(bindings, activation, DIKey.get[T])

  /** Instantiate `T @Id(name)` and the dependencies of `T @Id(name)`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    *
    * @see [[izumi.distage.model.definition.Id @Id annotation]]
    */
  def target[T: Tag](name: String)(bindings: ModuleBase, activation: Activation): PlannerInput = PlannerInput(bindings, activation, DIKey.get[T].named(name))

  /** Disable all dependency pruning. Every binding in `bindings` will be instantiated, without selection of the root components. There's almost always a better way to model things though. */
  def everything(bindings: ModuleBase, activation: Activation = Activation.empty): PlannerInput = PlannerInput(bindings, activation, Roots.Everything)
}
