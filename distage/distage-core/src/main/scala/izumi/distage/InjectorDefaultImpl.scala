package izumi.distage

import izumi.distage.model._
import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.monadic.DIEffect
import izumi.distage.model.plan.{AbstractPlan, DodgyPlan, OrderedPlan, SemiPlan}
import izumi.distage.model.provisioning.PlanInterpreter
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizersFilter}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK

class InjectorDefaultImpl(parentContext: Locator) extends Injector {

  private val planner: Planner = parentContext.get[Planner]
  private val interpreter = parentContext.get[PlanInterpreter]

  override def freeze(plan: DodgyPlan): SemiPlan = {
    planner.freeze(plan)
  }

  override def prepare(input: PlannerInput): DodgyPlan = {
    planner.prepare(input)
  }

  override def plan(input: PlannerInput): OrderedPlan = {
    planner.plan(input)
  }

  override def finish(semiPlan: SemiPlan): OrderedPlan = {
    planner.finish(semiPlan)
  }

  override def merge(a: AbstractPlan, b: AbstractPlan): OrderedPlan = {
    planner.merge(a, b)
  }

  override protected[distage] def produceFX[F[_] : TagK : DIEffect](plan: OrderedPlan, filter: FinalizersFilter[F]): DIResourceBase[F, Locator] = {
    produceDetailedFX[F](plan, filter).evalMap(_.throwOnFailure())
  }

  override protected[distage] def produceDetailedFX[F[_] : TagK : DIEffect](plan: OrderedPlan, filter: FinalizersFilter[F]): DIResourceBase[F, Either[FailedProvision[F], Locator]] = {
    interpreter.instantiate[F](plan, parentContext, filter)
  }
}
