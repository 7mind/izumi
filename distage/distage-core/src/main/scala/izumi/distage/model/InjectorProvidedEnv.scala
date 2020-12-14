package izumi.distage.model

import izumi.distage.InjectorFactory
import izumi.distage.model.definition.{BootstrapModule, Id, Module}
import izumi.distage.model.provisioning.PlanInterpreter

final case class InjectorProvidedEnv(
  injectorFactory: InjectorFactory,
  bootstrapModule: BootstrapModule,
  bootstrapLocator: Locator,
  defaultModule: Module @Id("defaultModule"),
  planner: Planner,
  interpreter: PlanInterpreter,
)
