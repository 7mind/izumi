package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.{ConfigArgsProvider, ConfigFilteringStrategy, ConfigLoader, ConfigLocationProvider, ConfigMerger}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.reflect.TagK

class RoleAppBootConfigModule[F[_]: TagK: DefaultModule] extends ModuleDef {
  make[ConfigLoader].fromClass[ConfigLoader.LocalFSImpl]
  make[ConfigMerger].fromClass[ConfigMerger.ConfigMergerImpl]
  make[ConfigLocationProvider].from(ConfigLocationProvider.Default)
  make[ConfigArgsProvider].fromClass[ConfigArgsProvider.Default]
  make[ConfigFilteringStrategy].fromClass[ConfigFilteringStrategy.Default]
  make[AppConfig].from {
    (configLoader: ConfigLoader) =>
      configLoader.loadConfig("application startup")
  }
}
