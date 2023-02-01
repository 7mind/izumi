package izumi.distage.roles

import distage.Activation
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.roles.launcher.RoleProvider
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.reflect.TagK

class RoleAppBootPlatformModule[F[_]: TagK: DefaultModule]() extends ModuleDef {
  include(new RoleAppBootConfigModule[F]())
  include(new RoleAppBootLoggerModule[F]())

  make[RoleProvider].from[RoleProvider.NonReflectiveImpl]

  make[PlanningOptions].fromValue(PlanningOptions())
  make[Activation].named("roleapp").fromValue(Activation.empty)
  make[RawAppArgs].fromValue(RawAppArgs.empty)
}
