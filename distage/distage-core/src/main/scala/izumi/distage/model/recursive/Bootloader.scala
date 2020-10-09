package izumi.distage.model.recursive

import izumi.distage.InjectorFactory
import izumi.distage.model.definition.{Activation, BootstrapModule, Id, Module, ModuleBase}
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.{OrderedPlan, Roots}
import izumi.distage.model.{Injector, PlannerInput}
import izumi.distage.modules.DefaultModule
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

final case class BootstrappedApp(
  injector: Injector[Identity],
  module: ModuleBase,
  plan: OrderedPlan,
)

final case class BootConfig(
  bootstrap: BootstrapModule => BootstrapModule = identity,
  appModule: ModuleBase => ModuleBase = identity,
  activation: Activation => Activation = identity,
  roots: Roots => Roots = identity,
)

class Bootloader(
  val bootstrapModule: BootstrapModule,
  val input: PlannerInput,
  val injectorFactory: InjectorFactory,
  val defaultModule: Module @Id("defaultModule"),
) {
  def boot(config: BootConfig): BootstrappedApp = {
    // FIXME: incorrect
    val activation = config.activation(input.activation)
    val bootstrap = config.bootstrap(bootstrapModule)
    val injector = injectorFactory
      .withBootstrapActivation[Identity](
        activation = activation,
        overrides = bootstrap,
      )(
        QuasiIO[Identity],
        TagK[Identity],
        DefaultModule(defaultModule),
      )
    val module = config.appModule(input.bindings)
    val roots = config.roots(input.roots)
    val plan = injector.plan(PlannerInput(module, activation, roots))
    BootstrappedApp(injector, module, plan)
  }
}
