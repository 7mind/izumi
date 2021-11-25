package izumi.distage.modules.typeclass

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Timer}
import cats.{Parallel, effect}
import distage.{Id, ModuleDef}
import izumi.reflect.Tag
import zio.{IO, Runtime, ZIO}

import java.util.concurrent.ThreadPoolExecutor
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, NANOSECONDS, TimeUnit}

/**
  * Adds `cats-effect` typeclass instances for ZIO
  *
  * Depends on `zio.Runtime[R]` and `ThreadPoolExecutor @Id("zio.io")` (both can be found in [[izumi.distage.modules.support.ZIOSupportModule]])
  *
  * Will also add the following components:
  *   - [[cats.effect.Blocker]] by using `ThreadPoolExecutor @Id("zio.io")`
  */
class ZIOCatsEffectInstancesModule[R: Tag] extends ModuleDef {
  include(CatsEffectInstancesModule[ZIO[R, Throwable, +_]])

  make[ConcurrentEffect[ZIO[R, Throwable, +_]]].from {
    r: Runtime[R] =>
      zio.interop.catz.taskEffectInstance(r)
  }
  make[Parallel[ZIO[R, Throwable, +_]]].from {
    zio.interop.catz.parallelInstance[R, Throwable]
  }
  make[Timer[ZIO[R, Throwable, +_]]].from[ZIOClockTimer[R, Throwable]]

  make[ContextShift[ZIO[R, Throwable, +_]]].from {
    zio.interop.catz.zioContextShift[R, Throwable]
  }
  make[Blocker].from {
    pool: ThreadPoolExecutor @Id("zio.io") =>
      Blocker.liftExecutionContext(ExecutionContext.fromExecutorService(pool))
  }
}

object ZIOCatsEffectInstancesModule extends ModuleDef {
  @inline final def apply[R: Tag](): ModuleDef = new ZIOCatsEffectInstancesModule[R]

  include(new ZIOCatsEffectInstancesModule[Any])
}

final class ZIOClockTimer[R, E](zioClock: zio.clock.Clock.Service) extends effect.Timer[ZIO[R, E, _]] {
  override lazy val clock: effect.Clock[ZIO[R, E, _]] = new effect.Clock[ZIO[R, E, _]] {
    override def monotonic(unit: TimeUnit): ZIO[R, E, Long] =
      zioClock.nanoTime.map(unit.convert(_, NANOSECONDS))

    override def realTime(unit: TimeUnit): ZIO[R, E, Long] =
      zioClock.currentTime(unit)
  }

  override def sleep(duration: FiniteDuration): IO[E, Unit] =
    zioClock.sleep(zio.duration.Duration.fromNanos(duration.toNanos))
}
