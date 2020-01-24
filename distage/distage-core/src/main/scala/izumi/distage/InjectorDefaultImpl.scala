package izumi.distage

import distage.Injector
import izumi.distage.model._
import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.definition.{ModuleBase, ModuleDef}
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.initial.PrePlan
import izumi.distage.model.plan.{OrderedPlan, SemiPlan}
import izumi.distage.model.provisioning.PlanInterpreter
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflective.Bootloader
import izumi.fundamentals.reflection.Tags.TagK

class InjectorDefaultImpl(parentContext: Locator) extends Injector {

  private val planner: Planner = parentContext.get[Planner]
  private val interpreter = parentContext.get[PlanInterpreter]

  override def plan(input: PlannerInput): OrderedPlan = {
    planner.plan(addImpl(input))
  }

  override def planNoRewrite(input: PlannerInput): OrderedPlan = {
    planner.planNoRewrite(addImpl(input))
  }

  override def prepare(input: PlannerInput): PrePlan = {
    planner.prepare(addImpl(input))
  }

  override def freeze(plan: PrePlan): SemiPlan = {
    planner.freeze(plan)
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    planner.rewrite(module)
  }

  override def finish(semiPlan: SemiPlan): OrderedPlan = {
    planner.finish(semiPlan)
  }


  override def truncate(plan: OrderedPlan, roots: Set[RuntimeDIUniverse.DIKey]): OrderedPlan = {
    planner.truncate(plan, roots)
  }

  override private[distage] def produceFX[F[_] : TagK : DIEffect](plan: OrderedPlan, filter: FinalizerFilter[F]): DIResourceBase[F, Locator] = {
    produceDetailedFX[F](plan, filter).evalMap(_.throwOnFailure())
  }

  override private[distage] def produceDetailedFX[F[_] : TagK : DIEffect](plan: OrderedPlan, filter: FinalizerFilter[F]): DIResourceBase[F, Either[FailedProvision[F], Locator]] = {
    interpreter.instantiate[F](plan, parentContext, filter)
  }

  private def addImpl(input: PlannerInput): PlannerInput = {
    val reflectionModule = new ModuleDef {
      make[PlannerInput].fromValue(input)
      make[InjectorApi].fromValue(Injector)
      make[Bootloader]
    }

    input.copy(bindings = input.bindings overridenBy reflectionModule)
  }
}
