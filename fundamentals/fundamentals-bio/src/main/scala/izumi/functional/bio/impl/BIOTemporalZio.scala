package izumi.functional.bio.impl

import izumi.functional.bio.instances.BIOTemporal3
import zio.clock.Clock
import zio.duration.Duration.fromScala
import zio.{Schedule, ZIO}

import scala.concurrent.duration.{Duration, FiniteDuration}

class BIOTemporalZio(private val clock: Clock) extends BIOAsyncZio with BIOTemporal3[ZIO] {
  @inline override final def sleep(duration: Duration): ZIO[Any, Nothing, Unit] = {
    ZIO.sleep(fromScala(duration)).provide(clock)
  }

  @inline override final def retryOrElse[R, A, E, A2 >: A, E2](r: ZIO[R, E, A])(duration: FiniteDuration, orElse: => ZIO[R, E2, A2]): ZIO[R, E2, A2] =
    ZIO.accessM {
      env =>
        val zioDuration = Schedule.duration(fromScala(duration))

        r.provide(env)
          .retryOrElse(zioDuration, (_: Any, _: Any) => orElse.provide(env))
          .provide(clock)
    }

  @inline override final def timeout[R, E, A](r: ZIO[R, E, A])(duration: Duration): ZIO[R, E, Option[A]] = {
    ZIO.accessM[R](e => race(r.provide(e).map(Some(_)).interruptible, sleep(duration).as(None).interruptible))
  }

}
