package izumi.functional.bio.impl

import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.functional.bio.{F, Temporal2}
import izumi.functional.bio.__VersionSpecificDurationConvertersCompat.toFiniteDuration
import izumi.functional.bio.retry.RetryPolicy.{ControllerDecision, RetryFunction}
import izumi.functional.bio.retry.{RetryPolicy, Scheduler2}

open class SchedulerImpl[F[+_, +_]: Temporal2] extends Scheduler2[F] {

  override def repeat[E, A, B](eff: F[E, A])(policy: RetryPolicy[F, A, B]): F[E, A] = {
    eff.flatMap(out => loop(out, policy.action)(F.pure(_))(eff.flatMap))
  }

  override def retry[E, S, A](eff: F[E, A])(policy: RetryPolicy[F, E, S]): F[E, A] = {
    retryOrElse(eff)(policy)(F.fail(_))
  }

  override def retryOrElse[E, E2, S, A, A1 >: A](eff: F[E, A])(policy: RetryPolicy[F, E, S])(orElse: E => F[E2, A1]): F[E2, A1] = {
    eff.catchAll(err => loop(err, policy.action)(orElse)(eff.catchAll))
  }

  def loop[E, S, A, B](
    in: A,
    makeDecision: RetryFunction[F, A, S],
  )(stopper: A => F[E, B]
  )(repeater: (A => F[E, B]) => F[E, B]
  ): F[E, B] = {
    (for {
      now <- F.clock.now(ClockAccuracy.MILLIS)
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

}
