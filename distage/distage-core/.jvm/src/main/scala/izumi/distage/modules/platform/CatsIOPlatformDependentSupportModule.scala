package izumi.distage.modules.platform

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import izumi.distage.model.definition.{Id, Lifecycle, ModuleDef}
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

private[distage] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ExecutionContext].named("io").fromResource {
    Lifecycle
      .makeSimple(
        acquire = IORuntime.createDefaultBlockingExecutionContext()
      )(release = _._2.apply()).map(_._1)
  }
  make[ExecutionContext].named("cpu").from((_: (IORuntime, ExecutionContext))._2)

  // by-name cycles don't work reliably at all, so unfortunately, manual cycle breaking:
  make[(IORuntime, ExecutionContext)].fromResource {
    (blockingPool: ExecutionContext @Id("io"), scheduler: Scheduler, ioRuntimeConfig: IORuntimeConfig) =>
      val cpuRef = new AtomicReference[ExecutionContext](null)
      lazy val ioRuntime: IORuntime = IORuntime(cpuRef.get(), blockingPool, scheduler, () => (), ioRuntimeConfig)
      CatsIOPlatformDependentSupportModule.createCPUPool.map {
        ec =>
          cpuRef.set(ec)
          (ioRuntime, ec)
      }
  }
  make[IORuntime].from((_: (IORuntime, ExecutionContext))._1)
}

object CatsIOPlatformDependentSupportModule {
  private[distage] def createCPUPool: Lifecycle[Identity, ExecutionContext] = {
    val coresOr2 = java.lang.Runtime.getRuntime.availableProcessors() max 2
    Lifecycle
      .makeSimple(
        acquire = IORuntime.createWorkStealingComputeThreadPool(threads = coresOr2)
        //      )(release = _._2.apply()).map(_._1)
      )(release = _ => ()).map(_._1) // FIXME ignore finalizer due to upstream bug https://github.com/typelevel/cats-effect/issues/3006
  }
}
