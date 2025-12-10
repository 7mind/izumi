package izumi.distage.model.plan.topology

import izumi.distage.model.plan.topology.DepTreeNode.DepNode
import izumi.distage.model.plan.topology.DependencyGraph.DependencyKind
import izumi.distage.model.reflection.*
import izumi.fundamentals.graphs.struct.AdjacencyList

import scala.collection.mutable

final case class DependencyGraph(matrix: AdjacencyList[DIKey], kind: DependencyKind) {

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitive(key: DIKey): Set[DIKey] = {
    val out = mutable.Set.empty[DIKey]
    computeTransitiveDeps(key, out)
    out.toSet
  }

  def tree(root: DIKey, depth: Option[Int] = None): DepNode = {
    DepNode(root, this, 0, depth, Set.empty)
  }

  def direct(key: DIKey): Set[DIKey] = matrix.links(key)

  def contains(key: DIKey): Boolean = matrix.links.contains(key)

  private def computeTransitiveDeps(key: DIKey, acc: mutable.Set[DIKey]): Unit = {
    val deps = matrix.links.getOrElse(key, Set.empty)
    val withoutItself = deps.filterNot(_ == key)
    val toFetch = withoutItself.diff(acc)
    acc ++= deps

    toFetch.foreach {
      dep =>
        computeTransitiveDeps(dep, acc)
    }
  }
}

object DependencyGraph {
  sealed trait DependencyKind
  object DependencyKind {
    case object Depends extends DependencyKind
    case object Required extends DependencyKind
  }
}
