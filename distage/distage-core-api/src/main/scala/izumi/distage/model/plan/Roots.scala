package izumi.distage.model.plan

import izumi.distage.model.reflection._
import izumi.fundamentals.collections.nonempty.NonEmptySet
import izumi.reflect.Tag

/**
  * `Roots` designate the components to choose as "garbage collection roots" for the object graph,
  * designated components and their dependencies will be created and
  *
  * The purpose of the `Roots`/`garbage collection` mechanism is to allow you is to allow
  * oversupplying the injector with bindings that may not be required to create a specific object graph,
  * instead of carefully curating bindings to shape the graph, choosing root nodes allows you to "pluck"
  * the sub-graph that you care about and instantiate just that sub-graph, ignoring all the other bindings.
  *
  * `distage-testkit` and `distage-framework`'s Roles make heavy use of the mechanism.
  * Roles are effectively just a root components that come with command-line arguments.
  * distage-testkit's test cases designate their parameters as roots and instantiate only the sub-graph
  * required for the test case.
  *
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
  def apply(roots: Set[_ <: DIKey]): Roots = {
    require(roots.nonEmpty, "GC roots set cannot be empty")
    Roots.Of(NonEmptySet.from(roots.toSet[DIKey]).get)
  }
  def target[T: Tag]: Roots = Roots(Set(DIKey.get[T]))
  def target[T: Tag](name: String): Roots = Roots(Set(DIKey.get[T].named(name)))

  final case class Of(roots: NonEmptySet[DIKey]) extends Roots

  /** Disable garbage collection and try to instantiate every single binding. */
  case object Everything extends Roots

  @deprecated("GCMode.NoGC has been renamed to `Roots.Everything`", "old name will be deleted in 1.1.1")
  lazy val NoGC: Everything.type = Everything
}
