package izumi.distage.effect.modules

import java.util.concurrent.ThreadPoolExecutor

import cats.effect.{Blocker, ConcurrentEffect, Timer}
import distage.{Id, ModuleDef}
import zio.interop.catz
import zio.{Runtime, Task}

import scala.concurrent.ExecutionContext

object ZIOCatsTypeclassesModule extends ZIOCatsTypeclassesModule

/**
  * Adds [[cats.effect]] typeclass instances for ZIO
  *
  * Depends on [[ZIODIEffectModule]]
  */
trait ZIOCatsTypeclassesModule extends ModuleDef {
  include(PolymorphicCatsTypeclassesModule[Task])

  make[ConcurrentEffect[Task]].from {
    r: Runtime[Any] =>
      catz.taskEffectInstance(r)
  }
  make[Timer[Task]].from(catz.implicits.ioTimer[Throwable])

  make[Blocker].from {
    pool: ThreadPoolExecutor @Id("zio.io") =>
      Blocker.liftExecutionContext(ExecutionContext.fromExecutorService(pool))
  }
}
