package izumi.distage.model.plan

import izumi.distage.model.reflection._
import izumi.fundamentals.reflection.Tags.Tag

sealed trait GCMode {
  def toSet: Set[DIKey]
}

object GCMode {
  def apply(root: DIKey, roots: DIKey*): GCMode = {
    GCRoots(roots.toSet + root)
  }
  def apply(roots: Set[_ <: DIKey]): GCMode = {
    GCMode.GCRoots(roots.toSet)
  }
  def target[T: Tag]: GCMode = GCMode(Set(DIKey.get[T]))
  def target[T: Tag](name: String): GCMode = GCMode(Set(DIKey.get[T].named(name)))

  final case class GCRoots(roots: Set[DIKey]) extends GCMode {
    assert(roots.nonEmpty, "GC roots set cannot be empty")

    override def toSet: Set[DIKey] = roots
  }

  case object NoGC extends GCMode {
    override def toSet: Set[DIKey] = Set.empty
  }

}
