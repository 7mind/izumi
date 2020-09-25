package izumi.distage.modules.typeclass

import java.util.concurrent.ThreadPoolExecutor

import cats.Parallel
import cats.effect.{Blocker, ConcurrentEffect, Timer}
import distage.{Id, ModuleDef}
import zio.interop.catz
import zio.{Runtime, Task}

import scala.concurrent.ExecutionContext

object ZIOCatsEffectInstancesModule extends ZIOCatsEffectInstancesModule

/**
  * Adds `cats-effect` typeclass instances for ZIO
  *
  * Depends on `make[zio.Runtime[Any]]` and `make[ThreadPoolExecutor].named("zio.io")` (both may be found in [[izumi.distage.modules.support.ZIOSupportModule]]
  */
trait ZIOCatsEffectInstancesModule extends ModuleDef {
  include(CatsEffectInstancesModule[Task])

  make[ConcurrentEffect[Task]].from {
    r: Runtime[Any] =>
      catz.taskEffectInstance(r)
  }
  make[Parallel[Task]].from(catz.parallelInstance[Any, Throwable])
  make[Timer[Task]].from(catz.implicits.ioTimer[Throwable])

  make[Blocker].from {
    pool: ThreadPoolExecutor @Id("zio.io") =>
      Blocker.liftExecutionContext(ExecutionContext.fromExecutorService(pool))
  }
}
