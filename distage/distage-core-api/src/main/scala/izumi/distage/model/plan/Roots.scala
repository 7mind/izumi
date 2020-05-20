package izumi.distage.model.plan

import izumi.distage.model.reflection._
import izumi.fundamentals.collections.nonempty.NonEmptySet
import izumi.reflect.Tag

/**
  *
  */
sealed trait Roots

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

  /**
    *
    */
  case object Everything extends Roots

  @deprecated("GCMode.NoGC has been renamed to `Roots.Everything`", "old name will be deleted in 0.11.1")
  lazy val NoGC: Everything.type = Everything
}
