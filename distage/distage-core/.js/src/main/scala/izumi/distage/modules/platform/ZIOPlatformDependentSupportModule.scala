package izumi.distage.modules.platform

import izumi.distage.model.definition.{Id, ModuleDef}
import izumi.functional.bio.UnsafeRun2.FailureHandler
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform
import zio.internal.tracing.TracingConfig
import zio.random.Random
import zio.system.System
import zio.{Has, Runtime, ZEnv}
import izumi.reflect.Tag

import scala.concurrent.ExecutionContext

private[modules] abstract class ZIOPlatformDependentSupportModule[R: Tag] extends ModuleDef {
  make[ZEnv].from(Has.allOf(_: Clock.Service, _: Console.Service, _: System.Service, _: Random.Service))

  make[TracingConfig].fromValue(TracingConfig.enabled)
  make[FailureHandler].fromValue(FailureHandler.Default)
  make[Runtime[R]].from((initialEnv: R @Id("zio-initial-env")) => zio.Runtime.default.map(_ => initialEnv))
  make[Platform].from((_: Runtime[R]).platform)

  make[ExecutionContext].named("zio.cpu").from((_: Platform).executor.asEC)
}
