package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

/**
  * This class represents direct node dependencies and allows to retrive full transitive dependencies for a node
  */
sealed trait PlanTopology {
  def dependees: Map[DIKey, Set[DIKey]]

  def dependencies: Map[DIKey, Set[DIKey]]

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitiveDependees(key: DIKey): Set[DIKey] = {
    val out = mutable.Set.empty[DIKey]
    transitiveDependees(key, out)
    out.toSet
  }

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitiveDependencies(key: DIKey): Set[DIKey] = {
    val out = mutable.Set.empty[DIKey]
    transitiveDependencies(key, out)
    out.toSet
  }

  private def transitiveDependees(key: DIKey, acc: mutable.Set[DIKey]): Unit = {
    val deps = dependees.getOrElse(key, Set.empty)
    val withoutItself = deps.filterNot(_ == key)
    val toFetch = withoutItself.diff(acc)
    acc ++= deps

    toFetch.foreach {
      dep =>
        transitiveDependees(dep, acc)
    }
  }

  private def transitiveDependencies(key: DIKey, acc: mutable.Set[DIKey]): Unit = {
    val deps = dependencies.getOrElse(key, Set.empty)
    val withoutItself = deps.filterNot(_ == key)
    val toFetch = withoutItself.diff(acc)
    acc ++= deps

    toFetch.foreach {
      dep =>
        transitiveDependencies(dep, acc)
    }
  }
}

final case class PlanTopologyImmutable(
                                  dependees: Map[DIKey, Set[DIKey]]
                                  , dependencies: Map[DIKey, Set[DIKey]]
                                ) extends PlanTopology




