package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.PlanTopology.{DepMap, ReverseDepMap}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.mutable

final case class PlanTopology(
                               dependees: ReverseDepMap
                              , dependencies: DepMap
                             ) {
  def register(target: DIKey, opDeps: Set[DIKey]): Unit = {
    dependencies.getOrElseUpdate(target, mutable.Set.empty[DIKey])
    dependees.getOrElseUpdate(target, mutable.Set.empty[DIKey])

    opDeps.foreach {
      opDep =>
        dependees.addBinding(opDep, target)
        dependencies.addBinding(target, opDep)
    }
  }

  def depMap: Map[DIKey, Set[DIKey]] = dependencies.mapValues(_.toSet).toMap

}

object PlanTopology {
  type ReverseDepMap = mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
  type DepMap = mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]

  def empty: PlanTopology = PlanTopology(
    new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
    , new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]
  )
}
