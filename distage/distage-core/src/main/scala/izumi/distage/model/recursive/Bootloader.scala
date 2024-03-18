package izumi.distage.model.recursive

import izumi.distage.InjectorFactory
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.{Activation, BootstrapModule, Id, Module, ModuleBase}
import izumi.functional.quasi.QuasiIO
import izumi.distage.model.plan.{Plan, Roots}
import izumi.distage.model.{Injector, PlannerInput}
import izumi.distage.modules.DefaultModule
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

final case class BootstrappedApp(
  injector: Injector[Identity],
  module: ModuleBase,
  plan: Plan,
)

final case class BootConfig(
  bootstrap: BootstrapModule => BootstrapModule = identity,
  appModule: ModuleBase => ModuleBase = identity,
  activation: Activation => Activation = identity,
  bootstrapActivation: Activation => Activation = identity,
  roots: Roots => Roots = identity,
)

class Bootloader(
  val injectorFactory: InjectorFactory,
  val bootstrapModule: BootstrapModule,
  val bootstrapActivation: Activation @Id("bootstrapActivation"),
  val defaultModule: Module @Id("defaultModule"),
  val input: PlannerInput,
) {
  def boot(config: BootConfig): Either[NEList[DIError], BootstrappedApp] = {
    val activation = config.activation(input.activation)
    val bootstrap = config.bootstrap(bootstrapModule)
    val injector = injectorFactory(
      bootstrapActivation = config.bootstrapActivation(bootstrapActivation),
      overrides = Seq(bootstrap),
    )(
      QuasiIO[Identity],
      TagK[Identity],
      DefaultModule[Identity](defaultModule),
    )
    val module = config.appModule(input.bindings)
    val roots = config.roots(input.roots)

    for {
      plan <- injector.plan(PlannerInput(module, activation, roots))
    } yield {
      BootstrappedApp(injector, module, plan)
    }
  }
}
