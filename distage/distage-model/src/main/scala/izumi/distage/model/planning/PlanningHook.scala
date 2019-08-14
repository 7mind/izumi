package izumi.distage.model.planning

import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.distage.model.plan.{DodgyPlan, SemiPlan, OrderedPlan}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.fundamentals.platform.language.Quirks

trait PlanningHook {
  def hookWiring(binding: Binding.ImplBinding, wiring: RuntimeDIUniverse.Wiring): RuntimeDIUniverse.Wiring = {
    Quirks.discard(binding)
    wiring
  }

  def hookDefinition(defn: ModuleBase): ModuleBase = defn

  def phase00PostCompletion(plan: DodgyPlan): DodgyPlan = plan

  def phase10PostGC(plan: SemiPlan): SemiPlan = plan

  def phase20Customization(plan: SemiPlan): SemiPlan = plan

  def phase45PreForwardingCleanup(plan: SemiPlan): SemiPlan = plan

  def phase50PreForwarding(plan: SemiPlan): SemiPlan = plan

  def phase90AfterForwarding(plan: OrderedPlan): OrderedPlan = plan

}
