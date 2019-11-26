package izumi.distage.model.plan

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait GCMode {
  def toSet: Set[DIKey]

  final def ++(that: GCMode): GCMode = (this, that) match {
    case (GCMode.NoGC, _) => GCMode.NoGC
    case (_, GCMode.NoGC) => GCMode.NoGC
    case (GCMode.GCRoots(aRoots), GCMode.GCRoots(bRoots)) =>
      GCMode.GCRoots(aRoots ++ bRoots)
  }
}

object GCMode {
  def apply(key: DIKey, more: DIKey*): GCMode = GCRoots(more.toSet + key)

  final case class GCRoots(roots: Set[DIKey]) extends GCMode {
    assert(roots.nonEmpty, "GC roots set cannot be empty")

    override def toSet: Set[DIKey] = roots
  }

  case object NoGC extends GCMode {
    override def toSet: Set[DIKey] = Set.empty
  }

}