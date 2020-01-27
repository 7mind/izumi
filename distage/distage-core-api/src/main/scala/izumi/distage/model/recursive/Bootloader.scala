package izumi.distage.model.recursive

import izumi.distage.InjectorApi
import izumi.distage.model.definition.{BootstrapModule, ModuleBase}
import izumi.distage.model.plan.{GCMode, OrderedPlan}
import izumi.distage.model.{Injector, PlannerInput}

final case class BootstrappedApp(
                                  injector: Injector,
                                  module: ModuleBase,
                                  plan: OrderedPlan,
                                )

final case class BootConfig(
                             bootstrap: BootstrapModule => BootstrapModule = identity,
                             appModule: ModuleBase => ModuleBase = identity,
                             gcMode: GCMode => GCMode = identity,
                           )

class Bootloader(
  val bootstrapModule: BootstrapModule,
  val input: PlannerInput,
  api: InjectorApi,
) {
  def boot(config: BootConfig): BootstrappedApp = {
    val injector = api.apply(config.bootstrap(bootstrapModule))
    val module = config.appModule(input.bindings)
    val roots = config.gcMode(input.mode)
    val plan = injector.plan(PlannerInput(module, roots))
    BootstrappedApp(injector, module, plan)
  }
}
