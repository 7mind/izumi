package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.{ConfigArgsProvider, ConfigLoader, ConfigLocationProvider, ConfigMerger}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.reflect.TagK

class RoleAppBootConfigModule[F[_]: TagK: DefaultModule]() extends ModuleDef {
  make[ConfigLoader].from[ConfigLoader.LocalFSImpl]
  make[ConfigMerger].from[ConfigMerger.ConfigMergerImpl]
  make[ConfigLocationProvider].from(ConfigLocationProvider.Default)
  make[ConfigArgsProvider].from[ConfigArgsProvider.Default]
  make[Boolean].named("distage.roles.always-include-reference-role-configs").fromValue(false)
  make[AppConfig].from {
    (configLoader: ConfigLoader) =>
      configLoader.loadConfig("application startup")
  }
}
