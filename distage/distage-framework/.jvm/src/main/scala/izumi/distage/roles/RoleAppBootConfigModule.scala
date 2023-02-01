package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.reflect.TagK

class RoleAppBootConfigModule[F[_]: TagK: DefaultModule]() extends ModuleDef {
  make[ConfigLoader].from[ConfigLoader.LocalFSImpl]
  make[ConfigLoader.ConfigLocation].from(ConfigLoader.ConfigLocation.Default)
  make[ConfigLoader.Args].from(ConfigLoader.Args.makeConfigLoaderArgs _)
  make[AppConfig].from {
    (configLoader: ConfigLoader) =>
      configLoader.loadConfig()
  }
}
