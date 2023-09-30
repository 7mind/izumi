package izumi.distage.roles

import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.reflect.TagK

class RoleAppBootConfigModule[F[_]: TagK: DefaultModule]() extends ModuleDef {
  make[AppConfig].fromValue(AppConfig.empty)
}
