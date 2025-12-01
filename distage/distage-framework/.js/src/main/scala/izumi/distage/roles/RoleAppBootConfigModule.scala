package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.ModuleDef

class RoleAppBootConfigModule() extends ModuleDef {
  make[AppConfig].fromValue(AppConfig.empty)
}
