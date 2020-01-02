package izumi.distage.model.plan

import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait GCMode {
  def toSet: Set[DIKey]
}

object GCMode {
  def apply(key: DIKey, more: DIKey*): GCMode = {
    GCRoots(more.toSet + key, WeaknessPredicate.empty)
  }

  def fromSet(roots: Set[DIKey]): GCMode = {
    if (roots.nonEmpty) GCRoots(roots, WeaknessPredicate.empty) else NoGC
  }

  trait WeaknessPredicate {
    def judge(key: DIKey): Boolean
  }

  object WeaknessPredicate {
    final val empty: WeaknessPredicate = _ => false

    def apply(predicate: DIKey => Boolean): WeaknessPredicate = predicate(_)
  }

  final case class GCRoots(roots: Set[DIKey], weaknessPredicate: WeaknessPredicate) extends GCMode {
    assert(roots.nonEmpty, "GC roots set cannot be empty")

    override def toSet: Set[DIKey] = roots
  }

  case object NoGC extends GCMode {
    override def toSet: Set[DIKey] = Set.empty
  }

}
