package izumi.distage.model.definition.conflicts

import izumi.distage.model.planning.AxisPoint

sealed trait ConflictResolutionError[N, +V]

object ConflictResolutionError {
  final case class ConflictingAxisChoices[N](issues: Map[String, Set[AxisPoint]]) extends ConflictResolutionError[N, Nothing]
  final case class ConflictingDefs[N, V](defs: Map[MutSel[N], Set[(Set[AxisPoint], Node[N, V])]]) extends ConflictResolutionError[N, V]
  final case class UnsolvedConflicts[N](defs: Map[MutSel[N], Set[Annotated[N]]]) extends ConflictResolutionError[N, Nothing]
}

final case class Node[N, V](deps: Set[N], meta: V)
final case class Annotated[N](key: N, mut: Option[Int], axis: Set[AxisPoint]) {
  def withoutAxis: MutSel[N] = MutSel(key, mut)
  def isMutator: Boolean = mut.isDefined
}
final case class MutSel[N](key: N, mut: Option[Int]) {
  def isMutator: Boolean = mut.isDefined
  def asString: String = s"$key${mut.fold("")(i => s":$i")}"
}
