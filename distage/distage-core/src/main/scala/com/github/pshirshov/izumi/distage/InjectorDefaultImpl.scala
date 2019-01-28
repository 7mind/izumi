package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.plan.{AbstractPlan, OrderedPlan, SemiPlan}
import com.github.pshirshov.izumi.distage.model.provisioning.{FailedProvision, PlanInterpreter}

class InjectorDefaultImpl(parentContext: Locator) extends Injector {
  override def plan(input: PlannerInput): OrderedPlan = {
    parentContext.get[Planner].plan(input)
  }

  override def finish(semiPlan: SemiPlan): OrderedPlan = {
    parentContext.get[Planner].finish(semiPlan)
  }

  override def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan = {
    parentContext.get[Planner].merge(a, b)
  }

  override def produce(plan: OrderedPlan): Either[FailedProvision, Locator] = {
    parentContext.get[PlanInterpreter].instantiate(plan, parentContext)
  }
}
