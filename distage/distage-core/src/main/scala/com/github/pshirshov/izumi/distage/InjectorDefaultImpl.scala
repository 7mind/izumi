package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.monadic.DIMonad
import com.github.pshirshov.izumi.distage.model.plan.{AbstractPlan, OrderedPlan, SemiPlan}
import com.github.pshirshov.izumi.distage.model.provisioning.{FailedProvision, PlanInterpreter}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

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

  override def produce[F[_]: TagK: DIMonad](plan: OrderedPlan): F[Either[FailedProvision[F], Locator]] = {
    parentContext.get[PlanInterpreter].instantiate[F](plan, parentContext)
  }
}
