package izumi.distage.effect.modules

import java.util.concurrent.ThreadPoolExecutor

import cats.effect.{Blocker, ConcurrentEffect, Timer}
import distage.{Id, ModuleDef}
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{Runtime, Task}

import scala.concurrent.ExecutionContext

class ZIOCatsTypeclassesModule extends ModuleDef {
  include(PolymorphicCatsTypeclassesModule[Task])

  make[ConcurrentEffect[Task]].from {
    implicit r: Runtime[Any] =>
      implicitly[ConcurrentEffect[Task]]
  }
  make[Timer[Task]].from(Timer[Task])

  make[Blocker].from {
    pool: ThreadPoolExecutor @Id("zio.io") =>
      Blocker.liftExecutionContext(ExecutionContext.fromExecutorService(pool))
  }
}
