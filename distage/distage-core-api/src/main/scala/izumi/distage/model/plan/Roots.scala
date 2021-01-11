package izumi.distage.model.plan

import izumi.distage.model.reflection._
import izumi.fundamentals.collections.nonempty.NonEmptySet
import izumi.reflect.Tag

/**
  * `Roots` designate the components to choose as "garbage collection roots" for the object graph.
  *
  * The purpose of the `Roots`/`garbage collection` mechanism is to allow you to pass to the injector more bindings
  * than strictly necessary and defer the choice of what specific components to construct from those bindings.
  * Effectively, this selects a *sub-graph* of the largest possible object graph that can be described by bindings.
  *
  * Tests in `distage-testkit` and roles in `distage-framework` are built on this mechanism.
  * `distage-testkit`'s test cases designate their parameters as roots and instantiate only the sub-graph
  * required for a given test case. `distage-framework`'s roles are effectively just allow you to choose root components
  * and pass them command-line parameters.
  *
  * @see [[https://izumi.7mind.io/distage/basics#activation-axis               Activations       ]]
  * @see [[https://izumi.7mind.io/distage/advanced-features#garbage-collection Garbage Collection]]
  * @see [[https://izumi.7mind.io/distage/distage-framework#roles              Roles             ]]
  * @see [[https://izumi.7mind.io/distage/distage-testkit                      Testkit           ]]
  */
sealed trait Roots {
  final def ++(that: Roots): Roots = {
    (this, that) match {
      case (Roots.Of(a), Roots.Of(b)) => Roots.Of(a ++ b)
      case (Roots.Everything, _) => Roots.Everything
      case (_, Roots.Everything) => Roots.Everything
    }
  }
}

object Roots {
  def apply(root: DIKey, roots: DIKey*): Roots = {
    Roots.Of(NonEmptySet(root, roots: _*))
  }
  def apply(roots: NonEmptySet[_ <: DIKey]): Roots = {
    Roots.Of(roots.widen)
  }
  def apply(roots: Set[_ <: DIKey])(implicit d: DummyImplicit): Roots = {
    require(roots.nonEmpty, "GC roots set cannot be empty")
    Roots.Of(NonEmptySet.from(roots).get.widen)
  }
  def target[T: Tag]: Roots = Roots(NonEmptySet(DIKey.get[T]))
  def target[T: Tag](name: String): Roots = Roots(NonEmptySet(DIKey.get[T].named(name)))

  final case class Of(roots: NonEmptySet[DIKey]) extends Roots

  /** Disable garbage collection and try to instantiate every single binding. There's almost always a better way to model things though. */
  case object Everything extends Roots

  @deprecated("GCMode.NoGC has been renamed to `Roots.Everything`", "old name will be deleted in 1.1.1")
  lazy val NoGC: Everything.type = Everything
}
