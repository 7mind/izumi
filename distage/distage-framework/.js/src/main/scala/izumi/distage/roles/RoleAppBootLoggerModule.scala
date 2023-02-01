package izumi.distage.roles

import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter
import izumi.reflect.TagK

class RoleAppBootLoggerModule[F[_]: TagK: DefaultModule]() extends ModuleDef {
  make[IzLogger].named("early").fromValue(IzLogger())
  make[LogRouter].fromValue(IzLogger().router)

}
