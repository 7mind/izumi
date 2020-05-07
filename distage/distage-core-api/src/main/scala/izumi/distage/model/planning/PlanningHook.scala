package izumi.distage.model.planning

import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.plan.{OrderedPlan, SemiPlan}

trait PlanningHook {
  def hookDefinition(defn: ModuleBase): ModuleBase = defn

  def phase20Customization(plan: SemiPlan): SemiPlan = plan
  def phase50PreForwarding(plan: SemiPlan): SemiPlan = plan
  def phase90AfterForwarding(plan: OrderedPlan): OrderedPlan = plan
}
