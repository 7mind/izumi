package izumi.distage.roles

import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.ModuleDef

class RoleAppBootConfigModule() extends ModuleDef {
  make[ConfigLoader].from(ConfigLoader.empty)
}
