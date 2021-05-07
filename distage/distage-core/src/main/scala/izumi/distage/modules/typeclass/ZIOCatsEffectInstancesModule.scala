package izumi.distage.modules.typeclass

import java.util.concurrent.ThreadPoolExecutor

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Timer}
import cats.{Parallel, effect}
import distage.{Id, ModuleDef}
import zio.{IO, Runtime, Task}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, NANOSECONDS, TimeUnit}

object ZIOCatsEffectInstancesModule extends ZIOCatsEffectInstancesModule

/**
  * Adds `cats-effect` typeclass instances for ZIO
  *
  * Depends on `zio.Runtime[Any]` and `ThreadPoolExecutor @Id("zio.io")` (both can be found in [[izumi.distage.modules.support.ZIOSupportModule]])
  *
  * Will also add the following components:
  *   - [[cats.effect.Blocker]] by using `ThreadPoolExecutor @Id("zio.io")`
  */
trait ZIOCatsEffectInstancesModule extends ModuleDef {
  include(CatsEffectInstancesModule[Task])

  make[ConcurrentEffect[Task]].from {
    r: Runtime[Any] =>
      zio.interop.catz.taskEffectInstance(r)
  }
  make[Parallel[Task]].from {
    zio.interop.catz.parallelInstance[Any, Throwable]
  }
  make[Timer[Task]].from[ZIOClockTimer[Throwable]]

  make[ContextShift[Task]].from {
    zio.interop.catz.zioContextShift[Any, Throwable]
  }
  make[Blocker].from {
    pool: ThreadPoolExecutor @Id("zio.io") =>
      Blocker.liftExecutionContext(ExecutionContext.fromExecutorService(pool))
  }
}

final class ZIOClockTimer[E](zioClock: zio.clock.Clock.Service) extends effect.Timer[IO[E, _]] {
  override lazy val clock: effect.Clock[IO[E, _]] = new effect.Clock[IO[E, _]] {
    override def monotonic(unit: TimeUnit): IO[E, Long] =
      zioClock.nanoTime.map(unit.convert(_, NANOSECONDS))

    override def realTime(unit: TimeUnit): IO[E, Long] =
      zioClock.currentTime(unit)
  }

  override def sleep(duration: FiniteDuration): IO[E, Unit] =
    zioClock.sleep(zio.duration.Duration.fromNanos(duration.toNanos))
}
