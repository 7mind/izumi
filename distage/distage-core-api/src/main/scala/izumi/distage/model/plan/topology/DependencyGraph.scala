package izumi.distage.model.plan.topology

import izumi.distage.model.plan.topology.DepTreeNode.DepNode
import izumi.distage.model.plan.topology.DependencyGraph.DependencyKind
import izumi.distage.model.reflection._

import scala.collection.mutable

final case class DependencyGraph(graph: Map[DIKey, Set[DIKey]], kind: DependencyKind) {

  def dropDepsOf(keys: Set[DIKey]): DependencyGraph = {
    kind match {
      case DependencyKind.Depends =>
        val filtered = graph.view
          .flatMap {
            case (k, _) if keys.contains(k) =>
              Seq(k -> Set.empty[DIKey])
            case o => Seq(o)
          }
          .toMap
        DependencyGraph(filtered, kind)
      case DependencyKind.Required =>
        val filtered = graph.view
          .flatMap {
            case (k, v)  =>
              Seq(k -> v.diff(keys))
          }
          .toMap
        DependencyGraph(filtered, kind)
    }
  }

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

  def direct(key: DIKey): Set[DIKey] = graph(key)

  def contains(key: DIKey): Boolean = graph.contains(key)

  private def computeTransitiveDeps(key: DIKey, acc: mutable.Set[DIKey]): Unit = {
    val deps = graph.getOrElse(key, Set.empty)
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
    final case object Depends extends DependencyKind
    final case object Required extends DependencyKind
  }
}
