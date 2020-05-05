package izumi.distage.model.recursive

import izumi.distage.InjectorFactory
import izumi.distage.model.definition.{Activation, BootstrapModule, ModuleBase}
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
  activation: Activation => Activation = identity,
  gcMode: GCMode => GCMode = identity,
)

class Bootloader(
  val bootstrapModule: BootstrapModule,
  val activation: Activation,
  val input: PlannerInput,
  val injectorFactory: InjectorFactory,
) {
  def boot(config: BootConfig): BootstrappedApp = {
    val injector = injectorFactory(config.activation(activation), config.bootstrap(bootstrapModule))
    val module = config.appModule(input.bindings)
    val roots = config.gcMode(input.mode)
    val plan = injector.plan(PlannerInput(module, activation, roots))
    BootstrappedApp(injector, module, plan)
  }
}
