package izumi.functional.bio.retry

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.{SchedulerMonix, SchedulerZio}
import izumi.fundamentals.orphans.{`cats.effect.Timer`, `monix.bio.IO`}
import zio.ZIO

trait Scheduler2[F[+_, +_]] extends SchedulerInstances {
  def repeat[E, A, B](eff: F[E, A])(policy: RetryPolicy[F, A, B]): F[E, A]
  def retryOrElse[E, E1 >: E, E2, A, A2 >: A, S](eff: F[E, A])(policy: RetryPolicy[F, E1, S])(orElse: E1 => F[E2, A2]): F[E2, A2]
}

object Scheduler2 {
  def apply[F[+_, +_]: Scheduler2]: Scheduler2[F] = implicitly

  implicit final class Scheduler2Ops[F[+_, +_]](private val self: Scheduler2[F]) extends AnyVal {
    @inline def repeat[E, A, B](eff: F[E, A])(policy: RetryPolicy[F, A, B]): F[E, A] = self.repeat(eff)(policy)
    @inline def retryOrElse[E, E1 >: E, E2, A, A2 >: A, S](eff: F[E, A])(policy: RetryPolicy[F, E1, S])(orElse: E1 => F[E2, A2]): F[E2, A2] =
      self.retryOrElse[E, E1, E2, A, A2, S](eff)(policy)(orElse)
  }
}

private[bio] sealed trait SchedulerInstances
object SchedulerInstances extends SchedulerLowPriorityInstances {
  @inline implicit def SchedulerFromZio(implicit clockService: zio.clock.Clock): Predefined.Of[Scheduler3[ZIO]] =
    new SchedulerZio(clockService).asInstanceOf[Predefined.Of[Scheduler3[ZIO]]]
}

sealed trait SchedulerLowPriorityInstances {
  @inline implicit def SchedulerFromMonix[MonixBIO[+_, +_]: `monix.bio.IO`, Timer[_[_]]: `cats.effect.Timer`](
    implicit
    timer: Timer[MonixBIO[Nothing, _]]
  ): Predefined.Of[Scheduler2[MonixBIO]] = {
    new SchedulerMonix(timer.asInstanceOf[cats.effect.Timer[monix.bio.UIO]]).asInstanceOf[Predefined.Of[Scheduler2[MonixBIO]]]
  }
}
