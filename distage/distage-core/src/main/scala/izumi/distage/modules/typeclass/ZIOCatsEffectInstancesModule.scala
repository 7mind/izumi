package izumi.distage.modules.typeclass

import java.util.concurrent.ThreadPoolExecutor

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Timer}
import cats.{Parallel, effect}
import distage.{Id, ModuleDef}
import zio.interop.catz
import zio.{IO, Runtime, Task}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, NANOSECONDS, TimeUnit}

object ZIOCatsEffectInstancesModule extends ZIOCatsEffectInstancesModule

/**
  * Adds `cats-effect` typeclass instances for ZIO
  *
  * Depends on `make[zio.Runtime[Any]]` and `make[ThreadPoolExecutor].named("zio.io")`
  * (both may be found in [[izumi.distage.modules.support.ZIOSupportModule]])
  */
trait ZIOCatsEffectInstancesModule extends ModuleDef {
  include(CatsEffectInstancesModule[Task])

  make[ConcurrentEffect[Task]].from {
    r: Runtime[Any] =>
      catz.taskEffectInstance(r)
  }
  make[Parallel[Task]].from(catz.parallelInstance[Any, Throwable])
  make[Timer[Task]].from[ZIOClockTimer[Throwable]]

  make[ContextShift[Task]].from(catz.zioContextShift[Any, Throwable])
  make[Blocker].from {
    pool: ThreadPoolExecutor @Id("zio.io") =>
      Blocker.liftExecutionContext(ExecutionContext.fromExecutorService(pool))
  }
}

final class ZIOClockTimer[E](zioClock: zio.clock.Clock.Service) extends effect.Timer[IO[E, ?]] {
  override lazy val clock: effect.Clock[IO[E, ?]] = new effect.Clock[IO[E, ?]] {
    override def monotonic(unit: TimeUnit): IO[E, Long] =
      zioClock.nanoTime.map(unit.convert(_, NANOSECONDS))

    override def realTime(unit: TimeUnit): IO[E, Long] =
      zioClock.currentTime(unit)
  }

  override def sleep(duration: FiniteDuration): IO[E, Unit] =
    zioClock.sleep(zio.duration.Duration.fromNanos(duration.toNanos))
}
