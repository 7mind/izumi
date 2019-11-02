package izumi.distage.model.planning

import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.distage.model.plan.{DodgyPlan, OrderedPlan, SemiPlan}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring
import izumi.fundamentals.platform.language.Quirks._

trait PlanningHook {
  def hookDefinition(defn: ModuleBase): ModuleBase = defn
  def hookWiring(binding: Binding.ImplBinding, wiring: Wiring): Wiring = { binding.discard(); wiring }

  def phase00PostCompletion(plan: DodgyPlan): DodgyPlan = plan
  def phase10PostGC(plan: SemiPlan): SemiPlan = plan
  def phase20Customization(plan: SemiPlan): SemiPlan = plan
  def phase45PreForwardingCleanup(plan: SemiPlan): SemiPlan = plan
  def phase50PreForwarding(plan: SemiPlan): SemiPlan = plan
  def phase90AfterForwarding(plan: OrderedPlan): OrderedPlan = plan
}
