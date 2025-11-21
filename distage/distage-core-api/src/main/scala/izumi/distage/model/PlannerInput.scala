package izumi.distage.model

import izumi.distage.model.definition.{Activation, LocatorPrivacy, ModuleBase}
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
  roots: Roots,
  activation: Activation,
  locatorPrivacy: LocatorPrivacy,
)

object PlannerInput {
  private val DefaultPrivacy = LocatorPrivacy.PublicByDefault

  implicit class PlannerInputSyntax(private val input: PlannerInput) extends AnyVal {
    def withLocatorPrivacy(privacy: LocatorPrivacy): PlannerInput = input.copy(locatorPrivacy = privacy)
    def privateByDefault: PlannerInput = withLocatorPrivacy(LocatorPrivacy.PrivateByDefault)
    def publicByDefault: PlannerInput = withLocatorPrivacy(LocatorPrivacy.PublicByDefault)

    def withActivation(activation: Activation): PlannerInput = input.copy(activation = activation)
    def emptyActivation: PlannerInput = withActivation(Activation.empty)
  }

  def apply(bindings: ModuleBase, roots: Roots, activation: Activation = Activation.empty, locatorPrivacy: LocatorPrivacy = DefaultPrivacy): PlannerInput =
    new PlannerInput(bindings, roots, activation, locatorPrivacy)

  /** Instantiate `T` and the dependencies of `T`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    */
  def target[T: Tag](bindings: ModuleBase): PlannerInput =
    PlannerInput(bindings, Roots.target[T], Activation.empty, DefaultPrivacy)

  /** Instantiate `T @Id(name)` and the dependencies of `T @Id(name)`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    *
    * @see [[izumi.distage.model.definition.Id @Id annotation]]
    */
  def target[T: Tag](name: String)(bindings: ModuleBase): PlannerInput =
    PlannerInput(bindings, Roots.target[T](name), Activation.empty, DefaultPrivacy)

  /** Disable all dependency pruning. Every binding in `bindings` will be instantiated, without selection of the root components. There's almost always a better way to model things though. */
  def everything(bindings: ModuleBase, activation: Activation = Activation.empty, locatorPrivacy: LocatorPrivacy = DefaultPrivacy): PlannerInput =
    PlannerInput(bindings, Roots.Everything, activation, locatorPrivacy)

  /**
    * Instantiate `roots` and the dependencies of `roots`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    */
  def apply(bindings: ModuleBase, roots: NESet[? <: DIKey], activation: Activation): PlannerInput =
    PlannerInput(bindings, Roots(roots), activation, DefaultPrivacy)

  /**
    * Instantiate `roots` and the dependencies of `roots`, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    */
  def apply(bindings: ModuleBase, roots: Set[? <: DIKey], activation: Activation)(implicit d: DummyImplicit): PlannerInput =
    PlannerInput(bindings, Roots(roots), activation, DefaultPrivacy)

  /** Instantiate `root`, `roots` and their dependencies, discarding bindings that are unrelated.
    *
    * Effectively, this selects and creates a *sub-graph* of the largest possible object graph that can be described by `bindings`
    */
  def apply(bindings: ModuleBase, activation: Activation, root: DIKey, roots: DIKey*): PlannerInput =
    PlannerInput(bindings, Roots(root, roots*), activation, DefaultPrivacy)

}
