package izumi.distage.modules.platform

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, PollingSystem, Scheduler}
import izumi.distage.model.definition.{Id, Lifecycle, ModuleDef}
import izumi.distage.modules.platform.CatsIOPlatformDependentSupportModule.ComputePool
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.ExecutionContext

private[distage] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ComputePool].fromResource(CatsIOPlatformDependentSupportModule.createComputePool)
  make[ExecutionContext].named("cpu").from((_: ComputePool).executionContext)
  make[ExecutionContext].named("io").fromResource {
    Lifecycle
      .makeSimple(
        acquire = IORuntime.createDefaultBlockingExecutionContext()
      )(release = _._2.apply()).map(_._1)
  }

  make[IORuntime].fromResource {
    (compute: ExecutionContext @Id("cpu"), blocking: ExecutionContext @Id("io"), scheduler: Scheduler, config: IORuntimeConfig, pool: ComputePool) =>
      Lifecycle.makeSimple(
        acquire = IORuntime(compute, blocking, scheduler, List(pool.poller), () => (), config)
      )(release = _.shutdown())
  }
}

object CatsIOPlatformDependentSupportModule {
  private[distage] final case class ComputePool(executionContext: ExecutionContext, poller: PollingSystem#Api, shutdown: () => Unit)

  private[distage] def createComputePool: Lifecycle[Identity, ComputePool] = {
    Lifecycle.makeSimple(
      acquire = {
        val pollingSystem = IORuntime.createDefaultPollingSystem()
        val (compute, poller, shutdown) = IORuntime.createWorkStealingComputeThreadPool(pollingSystem = pollingSystem)
        ComputePool(compute, poller, shutdown)
      }
    )(release = _.shutdown())
  }
}
