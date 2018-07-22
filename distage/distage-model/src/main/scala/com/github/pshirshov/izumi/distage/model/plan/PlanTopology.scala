package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.PlanTopology.{DepMap, ReverseDepMap}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

sealed trait PlanTopology {
  def dependees: Map[DIKey, Set[DIKey]]
  def dependencies: Map[DIKey, Set[DIKey]]

  def transitiveDependees(key: DIKey): Set[DIKey] = {
    val out = mutable.Set.empty[DIKey]
    transitiveDependees(key, out)
    out.toSet
  }

  def transitiveDependencies(key: DIKey): Set[DIKey] = {
    val out = mutable.Set.empty[DIKey]
    transitiveDependencies(key, out)
    out.toSet
  }

  protected def transitiveDependees(key: DIKey, acc: mutable.Set[DIKey]): Unit = {
    val deps = dependees.getOrElse(key, Set.empty)
    val toFetch = deps.diff(acc).filterNot(_ == key)
    acc ++= deps

    toFetch.foreach {
      dep =>
        transitiveDependees(dep, acc)
    }
  }
  protected def transitiveDependencies(key: DIKey, acc: mutable.Set[DIKey]): Unit = {
    val deps = dependencies.getOrElse(key, Set.empty)
    val toFetch = deps.diff(acc).filterNot(_ == key)
    acc ++= deps

    toFetch.foreach {
      dep =>
        transitiveDependencies(dep, acc)
    }
  }

  def transitive: PlanTopology = {
    PlanTopologyImm(dependees.mapValues(_.flatMap(transitiveDependees)), dependencies.mapValues(_.flatMap(transitiveDependencies)))
  }
}

final case class PlanTopologyImm(
                                dependees: Map[DIKey, Set[DIKey]]
                                , dependencies: Map[DIKey, Set[DIKey]]
                              ) extends PlanTopology

final case class XPlanTopology(
                               dependees: ReverseDepMap
                              , dependencies: DepMap
                             ) {

  def register(target: DIKey, opDeps: Set[DIKey]): Unit = {
    dependencies.getOrElseUpdate(target, mutable.Set.empty[DIKey])
    dependees.getOrElseUpdate(target, mutable.Set.empty[DIKey])
    opDeps.foreach {
      d =>
        dependees.addBinding(d, target)
        dependencies.addBinding(target, d)
    }
  }

  def immutable: PlanTopology = {
    PlanTopologyImm(dependees.mapValues(_.toSet).toMap, dependencies.mapValues(_.toSet).toMap)
  }
}

object PlanTopology {
  def inverse(depMap: DepMap): Map[DIKey, Set[DIKey]] = {
    val m = new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]


    depMap.foreach {
      case (k, v) =>
        v.foreach {
          e =>
          m.addBinding(e, k)
        }
    }

    m.mapValues(_.toSet).toMap
  }

  type ReverseDepMap = mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
  type DepMap = mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]

  def empty: XPlanTopology = XPlanTopology(
    new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
    , new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
  )
}
