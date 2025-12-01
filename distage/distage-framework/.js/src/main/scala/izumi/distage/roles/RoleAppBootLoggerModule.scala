package izumi.distage.roles

import izumi.distage.model.definition.ModuleDef
import izumi.logstage.api.IzLogger
import izumi.logstage.api.logger.LogRouter

class RoleAppBootLoggerModule() extends ModuleDef {
  make[IzLogger].named("early").fromValue(IzLogger())
  make[LogRouter].fromValue(IzLogger().router)
}
