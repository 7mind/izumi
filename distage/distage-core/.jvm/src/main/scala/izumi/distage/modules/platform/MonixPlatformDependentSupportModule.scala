package izumi.distage.modules.platform

import cats.effect.Blocker
import izumi.distage.model.definition.{Id, Lifecycle, ModuleDef}
import monix.execution.Scheduler

private[modules] trait MonixPlatformDependentSupportModule extends ModuleDef {
  make[Scheduler].named("io").fromResource(Lifecycle.makeSimple(Scheduler.io())(_.shutdown()))
  make[Blocker].from(Blocker.liftExecutionContext(_: Scheduler @Id("io")))
}
