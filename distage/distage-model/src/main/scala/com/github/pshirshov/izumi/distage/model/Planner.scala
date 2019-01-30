package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.GCRootPredicate
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

case class PlannerInput(bindings: ModuleBase, roots: Set[DIKey])

trait Planner {
  def plan(input: PlannerInput): OrderedPlan

  def finish(semiPlan: SemiPlan, rootPredicate: GCRootPredicate): OrderedPlan

  def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan

  final def plan(input: ModuleBase): OrderedPlan = {
    plan(PlannerInput(input))
  }
}
