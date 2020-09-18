package izumi.functional.bio.impl

import java.util.concurrent.TimeUnit

import cats.effect.Timer
import izumi.functional.bio.BIOTemporal
import monix.bio.{IO, UIO}

import scala.concurrent.duration._

class BIOTemporalMonix(timer: Timer[UIO]) extends BIOAsyncMonix with BIOTemporal[IO] {
  @inline override def sleep(duration: Duration): IO[Nothing, Unit] = IO.sleep(FiniteDuration(duration.length, duration.unit))
  @inline override def retryOrElse[R, E, A, E2](r: IO[E, A])(duration: FiniteDuration, orElse: => IO[E2, A]): IO[E2, A] = {
    def loop(maxTime: Long): IO[E2, A] = {
      r.redeemCauseWith(
        _ =>
          for {
            now <- timer.clock.monotonic(TimeUnit.NANOSECONDS)
            res <- if (now < maxTime) loop(maxTime) else orElse
          } yield res,
        res => IO.pure(res),
      )
    }
    timer
      .clock.monotonic(TimeUnit.NANOSECONDS).map(_ + duration.toNanos)
      .flatMap(loop)
  }

  @inline override def timeout[R, E, A](duration: Duration)(r: IO[E, A]): IO[E, Option[A]] = {
    r.timeout(FiniteDuration(duration.length, duration.unit))
  }
}
