package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.reflection.universe
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

sealed trait GCMode {
  def toSet: Set[DIKey]
}
object GCMode {
  case class GCRoots(roots: Set[DIKey]) extends GCMode {
    assert(roots.nonEmpty, "GC roots set cannot be empty")
    override def toSet: Set[universe.RuntimeDIUniverse.DIKey] = roots
  }
  case object NoGC extends GCMode {
    override def toSet: Set[RuntimeDIUniverse.DIKey] = Set.empty
  }

  def apply(key: DIKey, more: DIKey*): GCMode = GCRoots(more.toSet + key)
}


/**
  * Input for [[Planner]]
  *
  * @param bindings Bindings probably created using [[com.github.pshirshov.izumi.distage.model.definition.ModuleDef]] DSL
  * @param mode     Garbage collection roots.
  *
  *                 Garbage collector will remove all bindings that aren't direct or indirect dependencies
  *                 of the chosen root DIKeys from the plan - they will never be instantiated.
  *
  *                 If left empty, garbage collection will not be performed – that would be equivalent to
  *                 designating all DIKeys as roots.
  */
final case class PlannerInput(
                               bindings: ModuleBase,
                               mode: GCMode,
                             )

object PlannerInput {
  def noGc(bindings: ModuleBase): PlannerInput = {
    new PlannerInput(bindings, GCMode.NoGC)
  }

  def apply(bindings: ModuleBase, roots: Set[DIKey]): PlannerInput = {
    new PlannerInput(bindings, GCMode.GCRoots(roots))
  }
}

trait Planner {
  def plan(input: PlannerInput): OrderedPlan

  def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan

  // plan lifecycle
  def prepare(input: PlannerInput): DodgyPlan

  def freeze(plan: DodgyPlan): SemiPlan

  def finish(semiPlan: SemiPlan): OrderedPlan

  final def plan(input: ModuleBase, gcMode: GCMode): OrderedPlan = plan(PlannerInput(input, gcMode))
}
