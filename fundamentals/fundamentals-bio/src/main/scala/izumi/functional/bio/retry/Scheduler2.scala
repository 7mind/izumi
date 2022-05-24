package izumi.functional.bio.retry

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.SchedulerImpl
import izumi.functional.bio.{PredefinedHelper, Temporal3}

trait Scheduler2[F[+_, +_]] extends SchedulerInstances with PredefinedHelper {
  def repeat[E, A, B](eff: F[E, A])(policy: RetryPolicy[F, A, B]): F[E, A]
  def retry[E, S, A](eff: F[E, A])(policy: RetryPolicy[F, E, S]): F[E, A]
  def retryOrElse[E, E2, S, A, A1 >: A](eff: F[E, A])(policy: RetryPolicy[F, E, S])(orElse: E => F[E2, A1]): F[E2, A1]
}

object Scheduler2 {
  def apply[F[+_, +_]: Scheduler2]: Scheduler2[F] = implicitly
}

private[bio] sealed trait SchedulerInstances
object SchedulerInstances {
  @inline implicit def SchedulerFromTemporal[F[-_, +_, +_]: Temporal3]: Predefined.Of[Scheduler3[F]] =
    Predefined(new SchedulerImpl[F[Any, +_, +_]])
}
