package izumi.distage.modules.platform

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio.UnsafeRun2.FailureHandler
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform
import zio.internal.tracing.TracingConfig
import zio.random.Random
import zio.system.System
import zio.{Has, Runtime, ZEnv}

import scala.concurrent.ExecutionContext

private[modules] trait ZIOPlatformDependentSupportModule extends ModuleDef {
  make[ZEnv].from(Has.allOf(_: Clock.Service, _: Console.Service, _: System.Service, _: Random.Service))

  make[TracingConfig].fromValue(TracingConfig.enabled)
  make[FailureHandler].fromValue(FailureHandler.Default)
  make[Runtime[Any]].from(zio.Runtime.default)
  make[Platform].from((_: Runtime[Any]).platform)

  make[ExecutionContext].named("zio.cpu").from((_: Platform).executor.asEC)
}
