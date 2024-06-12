package izumi.functional.bio.impl

import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.functional.bio.{Clock2, F, Temporal2}
import izumi.functional.bio.__VersionSpecificDurationConvertersCompat.toFiniteDuration
import izumi.functional.bio.retry.RetryPolicy.{ControllerDecision, RetryFunction}
import izumi.functional.bio.retry.{RetryPolicy, Scheduler2}

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.FiniteDuration

open class SchedulerImpl[F[+_, +_]: Temporal2](implicit clock: Clock2[F]) extends Scheduler2[F] {

  override def repeat[E, A, B](eff: F[E, A])(policy: RetryPolicy[F, A, B]): F[E, A] = {
    eff.flatMap(out => loop(out, policy.action)(F.pure(_))(eff.flatMap))
  }

  override def retry[E, S, A](eff: F[E, A])(policy: RetryPolicy[F, E, S]): F[E, A] = {
    retryOrElse(eff)(policy)(F.fail(_))
  }

  override def retryOrElse[E, E2, S, A, A1 >: A](eff: F[E, A])(policy: RetryPolicy[F, E, S])(orElse: E => F[E2, A1]): F[E2, A1] = {
    eff.catchAll(err => loop(err, policy.action)(orElse)(eff.catchAll))
  }

  protected def loop[E, S, A, B](
    in: A,
    makeDecision: RetryFunction[F, A, S],
  )(stopper: A => F[E, B]
  )(repeater: (A => F[E, B]) => F[E, B]
  ): F[E, B] = {
    (for {
      now <- F.clock.nowZoned(ClockAccuracy.MILLIS)
      dec <- makeDecision(now, in)
      next = dec match {
        case ControllerDecision.Repeat(_, interval, action) =>
          val sleepTime = toFiniteDuration(java.time.Duration.between(now, interval))
          F.sleep(sleepTime) *> repeater(loop(_, action)(stopper)(repeater))
        case ControllerDecision.Stop(_) =>
          stopper(in)
      }
    } yield next).flatten
  }

  override def retryOrElseUntil[E, A, E2](r: F[E, A])(duration: FiniteDuration, orElse: E => F[E2, A]): F[E2, A] = {
    def loop(maxTime: ZonedDateTime): F[E2, A] = {
      F.redeem[E, A, E2, A](r)(
        err = error =>
          F.flatMap[E2, ZonedDateTime, A](
            clock.nowZoned(ClockAccuracy.MILLIS)
          )(now => if (now.isBefore(maxTime)) loop(maxTime) else orElse(error)),
        succ = F.pure(_),
      )
    }

    F.flatMap[E2, ZonedDateTime, A](
      clock.nowZoned(ClockAccuracy.MILLIS)
    )(now => loop(maxTime = now.plus(duration.toMillis, ChronoUnit.MILLIS)))
  }

}
