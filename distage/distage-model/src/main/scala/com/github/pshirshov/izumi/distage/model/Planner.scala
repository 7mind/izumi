package com.github.pshirshov.izumi.distage.model

import com.github.pshirshov.izumi.distage.model.definition.ModuleBase
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

final case class PlannerInput(
                               bindings: ModuleBase,
                               /**
                                 * Garbage collection roots.
                                 *
                                 * Garbage collector will remove all bindings that aren't direct or indirect dependencies
                                 * of the chosen root DIKeys from the plan - they will never be instantiated.
                                 *
                                 * If left empty, garbage collection will not be performed – that would be equivalent to
                                 * designating all DIKeys as roots.
                                 */
                               roots: Set[DIKey],
                             )

object PlannerInput {
  def apply(bindings: ModuleBase, roots: DIKey*): PlannerInput = new PlannerInput(bindings, Set(roots: _*))
}

trait Planner {
  def plan(input: PlannerInput): OrderedPlan

  def finish(semiPlan: SemiPlan): OrderedPlan

  def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan

  final def plan(input: ModuleBase, roots: Set[DIKey] = Set.empty): OrderedPlan = plan(PlannerInput(input, roots))
}
