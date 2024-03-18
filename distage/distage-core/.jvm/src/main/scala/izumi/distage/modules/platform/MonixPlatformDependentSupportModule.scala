//package izumi.distage.modules.platform
//
//import cats.effect.Blocker
//import izumi.distage.model.definition.{Id, Lifecycle, ModuleDef}
//import monix.execution.Scheduler
//
//import scala.concurrent.ExecutionContext
//
//private[modules] trait MonixPlatformDependentSupportModule extends ModuleDef {
//  make[Scheduler].named("io").fromResource(Lifecycle.makeSimple(Scheduler.io())(_.shutdown()))
//  make[ExecutionContext].named("io").using[Scheduler]
//  make[Blocker].from(Blocker.liftExecutionContext(_: Scheduler @Id("io")))
//}
