package izumi.distage.modules.platform

import cats.effect.Blocker
import izumi.distage.model.definition.{Id, ModuleDef}
import monix.execution.Scheduler

private[modules] trait MonixPlatformDependentSupportModule extends ModuleDef {
  make[Scheduler].named("io").from(Scheduler.io())
  make[Blocker].from(Blocker.liftExecutionContext(_: Scheduler @Id("io")))
}
