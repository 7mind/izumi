package izumi.functional.bio.impl

import cats.effect.Timer
import izumi.functional.bio.Temporal2
import izumi.functional.bio.retry.RetryPolicy.{ControllerDecision, RetryFunction}
import izumi.functional.bio.retry.{RetryPolicy, Scheduler2, toZonedDateTime}
import monix.bio.{IO, UIO}

import java.util.concurrent.TimeUnit
import scala.jdk.DurationConverters._

class SchedulerMonix(timer: Timer[UIO]) extends Scheduler2[IO] {
  override def repeat[E, A, B](eff: IO[E, A])(policy: RetryPolicy[IO, A, B]): IO[E, A] = {
    def loop(in: A, makeDecision: RetryFunction[IO, A, B]): IO[E, A] = {
      (for {
        now <- timer.clock.monotonic(TimeUnit.MILLISECONDS).map(toZonedDateTime)
        dec <- makeDecision(now, in)
        res = dec match {
          case ControllerDecision.Repeat(_, interval, action) =>
            IO.sleep(java.time.Duration.between(now, interval).toScala) *> eff.flatMap(loop(_, action))
          case ControllerDecision.Stop(_) =>
            IO.pure(in)
        }
      } yield res).flatten
    }

    eff.flatMap(out => loop(out, policy.action))
  }

  override def retryOrElse[E, E1 >: E, E2, A, A2 >: A, S](eff: IO[E, A])(policy: RetryPolicy[IO, E1, S])(orElse: E1 => IO[E2, A2]): IO[E2, A2] = {
    def loop(in: E1, makeDecision: RetryFunction[IO, E1, S]): IO[E2, A2] = {
      (for {
        now <- timer.clock.monotonic(TimeUnit.MILLISECONDS).map(toZonedDateTime)
        dec <- makeDecision(now, in)
        res = dec match {
          case ControllerDecision.Repeat(_, interval, action) =>
            IO.sleep(java.time.Duration.between(now, interval).toScala) *> eff.catchAll(loop(_, action))
          case ControllerDecision.Stop(_) =>
            orElse(in)
        }
      } yield res).flatten
    }

    eff.catchAll(err => loop(err, policy.action))
  }
}
