package izumi.distage.modules.platform

import cats.effect.unsafe.IORuntime
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.ExecutionContext

private[distage] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ExecutionContext].named("io").fromResource {
    Lifecycle
      .makeSimple(
        acquire = IORuntime.createDefaultBlockingExecutionContext()
      )(release = _._2.apply()).map(_._1)
  }

  protected[this] def createCPUPool(ioRuntime: => IORuntime): Lifecycle[Identity, ExecutionContext] = {
    val coresOr2 = java.lang.Runtime.getRuntime.availableProcessors() max 2
    Lifecycle
      .makeSimple(
        acquire = IORuntime.createDefaultComputeThreadPool(ioRuntime, threads = coresOr2)
//      )(release = _._2.apply()).map(_._1)
      )(release = _ => ()).map(_._1) // FIXME ignore finalizer due to upstream bug https://github.com/typelevel/cats-effect/issues/3006
  }
}
