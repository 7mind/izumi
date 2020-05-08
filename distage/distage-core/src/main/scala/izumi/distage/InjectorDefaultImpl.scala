package izumi.distage

import izumi.distage.model.definition.DIResource.DIResourceBase
import izumi.distage.model.definition.{Activation, BootstrapModule, ModuleBase, ModuleDef}
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.{OrderedPlan, SemiPlan}
import izumi.distage.model.provisioning.PlanInterpreter
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.{Injector, Locator, Planner, PlannerInput}
import izumi.fundamentals.reflection.Tags.TagK

class InjectorDefaultImpl(
  parentContext: Locator,
  parentFactory: InjectorFactory,
) extends Injector {

  private[this] val planner: Planner = parentContext.get[Planner]
  private[this] val interpreter: PlanInterpreter = parentContext.get[PlanInterpreter]
  private[this] val bsModule: BootstrapModule = parentContext.get[BootstrapModule]
  private[this] val activation: Activation = parentContext.get[Activation]

  override def plan(input: PlannerInput): OrderedPlan = {
    planner.plan(addSelfInfo(input))
  }

  override def planNoRewrite(input: PlannerInput): OrderedPlan = {
    planner.planNoRewrite(addSelfInfo(input))
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    planner.rewrite(module)
  }

  @deprecated("used in tests only!", "")
  override def finish(semiPlan: SemiPlan): OrderedPlan = {
    planner.finish(semiPlan)
  }

  private[distage] def truncate(plan: OrderedPlan, roots: Set[DIKey]): OrderedPlan = {
    planner.truncate(plan, roots)
  }

  override private[distage] def produceFX[F[_]: TagK: DIEffect](plan: OrderedPlan, filter: FinalizerFilter[F]): DIResourceBase[F, Locator] = {
    produceDetailedFX[F](plan, filter).evalMap(_.throwOnFailure())
  }

  override private[distage] def produceDetailedFX[F[_]: TagK: DIEffect](
    plan: OrderedPlan,
    filter: FinalizerFilter[F],
  ): DIResourceBase[F, Either[FailedProvision[F], Locator]] = {
    interpreter.instantiate[F](plan, parentContext, filter)
  }

  private[this] def addSelfInfo(input: PlannerInput): PlannerInput = {
    val selfReflectionModule = new ModuleDef {
      make[PlannerInput].fromValue(input)
      make[InjectorFactory].fromValue(parentFactory)
      make[BootstrapModule].fromValue(bsModule)
      make[Activation].fromValue(input.activation)
      make[Bootloader]
    }

    input.copy(bindings = input.bindings overridenBy selfReflectionModule)
  }
}
