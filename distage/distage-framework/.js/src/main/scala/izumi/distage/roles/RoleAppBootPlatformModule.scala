package izumi.distage.roles

import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.roles.launcher.RoleProvider
import izumi.reflect.TagK

class RoleAppBootPlatformModule[F[_]: TagK: DefaultModule]() extends ModuleDef {
  include(new RoleAppBootConfigModule[F]())
  include(new RoleAppBootLoggerModule[F]())

  make[RoleProvider].from[RoleProvider.NonReflectiveImpl]
}
