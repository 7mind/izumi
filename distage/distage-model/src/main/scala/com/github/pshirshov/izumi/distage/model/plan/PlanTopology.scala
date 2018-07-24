package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable


case class DependencyGraph(graph: Map[DIKey, Set[DIKey]], kind: DependencyKind) {
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

/**
  * This class represents direct node dependencies and allows to retrive full transitive dependencies for a node
  */
sealed trait PlanTopology {
  def dependees: DependencyGraph

  def dependencies: DependencyGraph

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitiveDependees(key: DIKey): Set[DIKey] = dependees.transitive(key)

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitiveDependencies(key: DIKey): Set[DIKey] = dependencies.transitive(key)
}

final case class PlanTopologyImmutable(
                                        dependees: DependencyGraph
                                        , dependencies: DependencyGraph
                                      ) extends PlanTopology




