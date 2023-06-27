package izumi.functional.bio.retry

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.SchedulerImpl
import izumi.functional.bio.{Clock2, PredefinedHelper, Temporal2}

import scala.concurrent.duration.FiniteDuration

trait Scheduler2[F[+_, +_]] extends SchedulerInstances with PredefinedHelper {
  def repeat[E, A, B](eff: F[E, A])(policy: RetryPolicy[F, A, B]): F[E, A]
  def retry[E, S, A](eff: F[E, A])(policy: RetryPolicy[F, E, S]): F[E, A]
  def retryOrElse[E, E2, S, A, A1 >: A](eff: F[E, A])(policy: RetryPolicy[F, E, S])(orElse: E => F[E2, A1]): F[E2, A1]

  def retryOrElseUntil[E, A, E2](r: F[E, A])(duration: FiniteDuration, orElse: E => F[E2, A]): F[E2, A]
}

object Scheduler2 {
  def apply[F[+_, +_]: Scheduler2]: Scheduler2[F] = implicitly
}

private[bio] sealed trait SchedulerInstances
object SchedulerInstances {
  @inline implicit def SchedulerFromTemporalAndClock[F[+_, +_]: Temporal2: Clock2]: Predefined.Of[Scheduler2[F]] =
    Predefined(new SchedulerImpl[F[+_, +_]])
}
