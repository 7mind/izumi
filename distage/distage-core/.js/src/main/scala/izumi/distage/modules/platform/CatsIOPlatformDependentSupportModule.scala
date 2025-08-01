package izumi.distage.modules.platform

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}
import izumi.distage.model.definition.{Id, Lifecycle, ModuleDef}
import izumi.fundamentals.platform.functional.Identity

import scala.concurrent.ExecutionContext

private[distage] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[ExecutionContext].named("cpu").fromValue(ExecutionContext.global)
  make[ExecutionContext].named("io").using[ExecutionContext]("cpu")

  make[IORuntime].from {
    (compute: ExecutionContext @Id("cpu"), blocking: ExecutionContext @Id("io"), scheduler: Scheduler, config: IORuntimeConfig) =>
      IORuntime(compute = compute, blocking = blocking, scheduler = scheduler, shutdown = () => (), config = config)
  }
}

object CatsIOPlatformDependentSupportModule {
  private[distage] def createCPUPool: Lifecycle[Identity, ExecutionContext] = {
    Lifecycle.pure(IORuntime.defaultComputeExecutionContext)
  }
}
