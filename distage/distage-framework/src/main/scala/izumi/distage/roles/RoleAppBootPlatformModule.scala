package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.ModuleDef
import izumi.distage.roles.launcher.RoleProvider

class RoleAppBootPlatformModule() extends ModuleDef {
  include(new RoleAppBootConfigModule())
  include(new RoleAppBootLoggerModule())

  make[RoleProvider].from[RoleProvider.NonReflectiveImpl]
  make[AppConfig].from {
    (configLoader: ConfigLoader) =>
      configLoader.loadConfig("application startup")
  }
}
