package izumi.functional.bio.impl

import izumi.functional.bio.{BIOTemporal, Clock2}
import monix.bio.IO

import scala.concurrent.duration._

class BIOTemporalMonix(clock: Clock2[IO]) extends BIOAsyncMonix with BIOTemporal[IO] {
  @inline override def sleep(duration: Duration): IO[Nothing, Unit] = IO.sleep(FiniteDuration(duration.length, duration.unit))
  @inline override def retryOrElse[R, E, A, E2](r: IO[E, A])(duration: FiniteDuration, orElse: => IO[E2, A]): IO[E2, A] = {
    val boundary = clock.now().map(_.toEpochSecond + duration.toSeconds)
    def loop: IO[E2, A] = {
      r.redeemCauseWith(
        _ =>
          for {
            now <- clock.now()
            time <- boundary
            res <- if (now.toEpochSecond < time) loop else orElse
          } yield res,
        res => IO.pure(res),
      )
    }
    loop
  }

  @inline override def timeout[R, E, A](r: IO[E, A])(duration: Duration): IO[E, Option[A]] = r.timeout(FiniteDuration(duration.length, duration.unit))
}
