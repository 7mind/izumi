package izumi.distage.model.definition.conflicts

import izumi.distage.model.planning.AxisPoint
import izumi.fundamentals.platform.cache.CachedProductHashcode

final case class Node[N, V](deps: Set[N], meta: V) extends CachedProductHashcode

final case class Annotated[N](key: N, mut: Option[Int], axis: Set[AxisPoint]) extends CachedProductHashcode {
  def withoutAxis: MutSel[N] = MutSel(key, mut)
  def isMutator: Boolean = mut.isDefined
}

final case class MutSel[N](key: N, mut: Option[Int]) extends CachedProductHashcode {
  def isMutator: Boolean = mut.isDefined
  def asString: String = s"$key${mut.fold("")(i => s":$i")}"
}
