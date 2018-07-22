package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

final case class MutablePlanTopology(
                               dependees: mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
                              , dependencies: mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
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
    PlanTopologyImmutable(dependees.mapValues(_.toSet).toMap, dependencies.mapValues(_.toSet).toMap)
  }
}

object MutablePlanTopology {
  def empty: MutablePlanTopology = MutablePlanTopology(
    new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
    , new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
  )
}
