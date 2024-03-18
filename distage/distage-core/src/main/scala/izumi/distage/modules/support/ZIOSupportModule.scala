package izumi.distage.modules.support

import izumi.distage.model.definition.Id
import izumi.distage.modules.platform.ZIOPlatformDependentSupportModule
import izumi.functional.bio.*
import izumi.functional.bio.UnsafeRun2.{FailureHandler, ZIORunner}
import izumi.functional.bio.retry.{Scheduler2, SchedulerInstances}
import izumi.reflect.{Tag, TagK3}
import zio.{Executor, IO, Runtime, ZEnvironment, ZIO, ZLayer}

import scala.concurrent.ExecutionContext

object ZIOSupportModule {
  def apply[R: Tag]: ZIOSupportModule[R] = new ZIOSupportModule[R]
}

/**
  * `zio.ZIO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.functional.quasi.QuasiIO]] instances to support using ZIO in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds [[izumi.functional.bio]] typeclass instances for ZIO
  *
  * Will also add the following components:
  *   - `ExecutionContext @Id("cpu")` for CPU-bound tasks (will be used for all tasks by default by [[zio.Runtime]])
  *   - `ExecutionContext @Id("io")` for blocking IO tasks (tasks can be scheduled to it via [[izumi.functional.bio.BlockingIO2]] or [[zio.ZIO.blocking]])
  *
  * Added into scope by [[izumi.distage.modules.DefaultModule]].
  * If [[https://github.com/zio/interop-cats/ interop-cats]] library is on the classpath during compilation,
  * implicit [[izumi.distage.modules.DefaultModule.forZIOPlusCats]] will be picked up instead of [[izumi.distage.modules.DefaultModule.forZIO]]
  * and will add a module with `cats-effect` instances [[izumi.distage.modules.typeclass.ZIOCatsEffectInstancesModule]]
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
class ZIOSupportModule[R: Tag] extends ZIOPlatformDependentSupportModule[R] {
  include(AnyBIOSupportModule[ZIO[Any, +_, +_]])
  if (!(Tag[R] =:= Tag[Any])) {
    include(AnyBIOSupportModule[ZIO[R, +_, +_]])
  }

  addImplicit[TagK3[ZIO]]

  // assume default environment is `Any`, otherwise let the error message guide the user here.
  make[ZEnvironment[Any]].named("zio-initial-env").fromValue(ZEnvironment.empty)

  make[UnsafeRun2[ZIO[R, _, _]]].using[ZIORunner[R]]

  make[BlockingIO2[ZIO[R, +_, +_]]].from(BlockingIOInstances.BlockingZIODefaultR[ZIO, R])

  make[ZIORunner[R]].from {
    (
      cpuPool: Executor @Id("cpu"),
      blockingPool: Executor @Id("io"),
      handler: FailureHandler,
      runtimeConfiguration: List[ZLayer[Any, Nothing, Any]] @Id("zio-runtime-configuration"),
      initialEnv: ZEnvironment[R] @Id("zio-initial-env"),
    ) =>
      UnsafeRun2.createZIO(
        customCpuPool = Some(cpuPool),
        customBlockingPool = Some(blockingPool),
        handler = handler,
        otherRuntimeConfiguration = runtimeConfiguration,
        initialEnv = initialEnv,
      )
  }
  make[FailureHandler].fromValue(FailureHandler.Default)
  make[List[ZLayer[Any, Nothing, Any]]].named("zio-runtime-configuration").fromValue(Nil)

  make[Executor].named("io").from {
    // no reason to use custom blocking pool, since this one is hardcoded in zio.internal.ZScheduler.submitBlocking
    Runtime.defaultBlockingExecutor
  }

  make[ExecutionContext].named("cpu").from((_: Executor @Id("cpu")).asExecutionContext)
  make[ExecutionContext].named("io").from((_: Executor @Id("io")).asExecutionContext)

  addImplicit[Async2[zio.IO]]
  addImplicit[Temporal2[zio.IO]]
  addImplicit[Fork2[zio.IO]]
  addImplicit[Primitives2[zio.IO]]
  addImplicit[PrimitivesM2[zio.IO]]
  if (!(Tag[R] =:= Tag[Any])) {
    addImplicit[Async2[ZIO[R, +_, +_]]]
    addImplicit[Temporal2[ZIO[R, +_, +_]]]
    addImplicit[Fork2[ZIO[R, +_, +_]]]
    addImplicit[Primitives2[ZIO[R, +_, +_]]]
    addImplicit[PrimitivesM2[ZIO[R, +_, +_]]]
  }

  make[Scheduler2[ZIO[R, +_, +_]]].from {
    SchedulerInstances.SchedulerFromTemporalAndClock(_: Temporal2[ZIO[R, +_, +_]], _: Clock2[ZIO[R, +_, +_]])
  }

  addImplicit[TransZio[IO]]
}
