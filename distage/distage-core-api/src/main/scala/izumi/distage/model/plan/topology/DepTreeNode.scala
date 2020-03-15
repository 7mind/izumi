package izumi.distage.model.plan.topology
import izumi.distage.model.reflection._

sealed trait DepTreeNode {
  def level: Int
}

object DepTreeNode {

  final case class Truncated(level: Int) extends DepTreeNode

  final case class CircularReference(key: DIKey, level: Int) extends DepTreeNode

  final case class DepNode(key: DIKey, graph: DependencyGraph, level: Int, limit: Option[Int], exclusions: Set[DIKey]) extends DepTreeNode {
    def children: Set[DepTreeNode] = {
      val children = graph.direct(key)

      if (limit.exists(_ <= level)) {
        Set(Truncated(level))
      } else {
        children.diff(exclusions).map {
          child =>
            DepNode(child, graph, level + 1, limit, exclusions + key)
        } ++ children.intersect(exclusions).map {
          child =>
            CircularReference(child, level + 1)
        }
      }
    }
  }

}
