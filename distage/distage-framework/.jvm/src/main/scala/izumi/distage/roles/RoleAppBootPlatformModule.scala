package izumi.distage.roles

import izumi.distage.model.definition.ModuleDef
import izumi.distage.roles.launcher.RoleProvider

class RoleAppBootPlatformModule() extends ModuleDef {
  include(new RoleAppBootConfigModule())
  include(new RoleAppBootLoggerModule())

  make[RoleProvider].from[RoleProvider.ReflectiveImpl]

}
