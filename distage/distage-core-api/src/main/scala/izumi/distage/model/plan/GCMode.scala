package izumi.distage.model.plan

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait GCMode {
  def toSet: Set[DIKey]
}

object GCMode {
  def apply(key: DIKey, more: DIKey*): GCMode = {
    GCRoots(more.toSet + key)
  }
  def fromSet(roots: Set[DIKey]): GCMode = {
    if (roots.nonEmpty) GCRoots(roots) else NoGC
  }

  final case class GCRoots(roots: Set[DIKey]) extends GCMode {
    assert(roots.nonEmpty, "GC roots set cannot be empty")

    override def toSet: Set[DIKey] = roots
  }

  case object NoGC extends GCMode {
    override def toSet: Set[DIKey] = Set.empty
  }

}
