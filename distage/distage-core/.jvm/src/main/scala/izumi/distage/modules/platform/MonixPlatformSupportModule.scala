package izumi.distage.modules.platform

import izumi.distage.model.definition.ModuleDef
import monix.execution.Scheduler

object MonixPlatformSupportModule extends ModuleDef {
  make[Scheduler].named("io").from(Scheduler.io())
}
