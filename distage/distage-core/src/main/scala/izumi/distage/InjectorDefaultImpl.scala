package izumi.distage

import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.{Activation, BootstrapModule, Module, ModuleBase, ModuleDef}
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.plan.OrderedPlan
import izumi.distage.model.provisioning.PlanInterpreter
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.{Injector, Locator, Planner, PlannerInput}
import izumi.reflect.TagK

final class InjectorDefaultImpl[F[_]](
  val parentContext: Locator,
  val parentFactory: InjectorFactory,
  val defaultModule: Module,
)(implicit
  override val F: DIEffect[F],
  override val tagK: TagK[F],
) extends Injector[F] {

  private[this] val planner: Planner = parentContext.get[Planner]
  private[this] val interpreter: PlanInterpreter = parentContext.get[PlanInterpreter]
  private[this] val bsModule: BootstrapModule = parentContext.get[BootstrapModule]

  override def plan(input: PlannerInput): OrderedPlan = {
    planner.plan(addSelfInfo(input))
  }

  override def planNoRewrite(input: PlannerInput): OrderedPlan = {
    planner.planNoRewrite(addSelfInfo(input))
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    planner.rewrite(module)
  }

  override private[distage] def produceDetailedFX[G[_]: TagK: DIEffect](
    plan: OrderedPlan,
    filter: FinalizerFilter[G],
  ): DIResourceBase[G, Either[FailedProvision[G], Locator]] = {
    interpreter.instantiate[G](plan, parentContext, filter)
  }

  private[this] def addSelfInfo(input: PlannerInput): PlannerInput = {
    val selfReflectionModule = new ModuleDef {
      make[PlannerInput].fromValue(input)
      make[InjectorFactory].fromValue(parentFactory)
      make[BootstrapModule].fromValue(bsModule)
      make[Activation].fromValue(input.activation)
      make[Module].named("defaultModule").fromValue(defaultModule)
      make[Bootloader]
    }

    input.copy(bindings =
      ModuleBase.make(
        ModuleBase
          .overrideImpl(
            ModuleBase.overrideImpl(
              defaultModule.iterator,
              input.bindings.iterator,
            ),
            selfReflectionModule.iterator,
          ).toSet
      )
    )
  }
}
