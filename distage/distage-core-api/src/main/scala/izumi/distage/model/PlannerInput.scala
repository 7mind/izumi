package izumi.distage.model

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.GCMode
import izumi.distage.model.plan.GCMode.WeaknessPredicate
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._

/**
  * Input for [[Planner]]
  *
  * @param bindings Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
  * @param mode     Garbage collection roots.
  *
  *                 Garbage collector will remove all bindings that aren't direct or indirect dependencies
  *                 of the chosen root DIKeys from the plan - they will never be instantiated.
  *
  *                 On [[GCMode.NoGC]] garbage collection will not be performed – that would be equivalent to
  *                 designating _all_ DIKeys as roots.
  */
final case class PlannerInput(
                               bindings: ModuleBase,
                               mode: GCMode,
                             )

object PlannerInput {
  def apply(bindings: ModuleBase, roots: Set[DIKey], weaknessPredicate: WeaknessPredicate): PlannerInput = {
    new PlannerInput(bindings, GCMode.GCRoots(roots, weaknessPredicate))
  }
  def noGc(bindings: ModuleBase): PlannerInput = {
    new PlannerInput(bindings, GCMode.NoGC)
  }
}
