package izumi.distage.roles

import izumi.distage.framework.services.{ConfigFilteringStrategy, ConfigLoader, ConfigLoaderArgs, ConfigLocationProvider, ConfigMerger}
import izumi.distage.model.definition.ModuleDef

class RoleAppBootConfigModule() extends ModuleDef {
  make[ConfigFilteringStrategy].from[ConfigFilteringStrategy.Default]
  make[ConfigMerger].from[ConfigMerger.ConfigMergerImpl]
  make[ConfigLocationProvider].from(ConfigLocationProvider.Default)
  make[ConfigLoaderArgs].from(ConfigLoaderArgs.fromRoleArgs _)
  make[ConfigLoader].from[ConfigLoader.LocalFSImpl]
}
