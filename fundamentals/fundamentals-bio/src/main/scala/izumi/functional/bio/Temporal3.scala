package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.{TemporalMonix, TemporalZio}
import zio.ZIO

import scala.concurrent.duration.{Duration, FiniteDuration}
import izumi.fundamentals.orphans.{`cats.effect.Timer`, `monix.bio.IO`}

trait Temporal3[F[-_, +_, +_]] extends RootBifunctor[F] with TemporalInstances {
  def InnerF: Error3[F]

  def sleep(duration: Duration): F[Any, Nothing, Unit]
  def timeout[R, E, A](duration: Duration)(r: F[R, E, A]): F[R, E, Option[A]]

  def retryOrElse[R, E, A, E2](r: F[R, E, A])(duration: FiniteDuration, orElse: => F[R, E2, A]): F[R, E2, A]

  @inline final def timeoutFail[R, E, A](duration: Duration)(e: E, r: F[R, E, A]): F[R, E, A] = {
    InnerF.flatMap(timeout(duration)(r))(_.fold[F[R, E, A]](InnerF.fail(e))(InnerF.pure))
  }

  @inline final def repeatUntil[R, E, A](action: F[R, E, Option[A]])(tooManyAttemptsError: => E, sleep: FiniteDuration, maxAttempts: Int): F[R, E, A] = {
    def go(n: Int): F[R, E, A] = {
      InnerF.flatMap(action) {
        case Some(value) =>
          InnerF.pure(value)
        case None =>
          if (n <= maxAttempts) {
            InnerF.*>(this.sleep(sleep), go(n + 1))
          } else {
            InnerF.fail(tooManyAttemptsError)
          }
      }
    }

    go(0)
  }
}

private[bio] sealed trait TemporalInstances
object TemporalInstances extends TemporalInstancesLowPriority1 {
  @inline implicit final def Temporal3Zio(implicit clockService: zio.clock.Clock): Predefined.Of[Temporal3[ZIO]] = Predefined(new TemporalZio(clockService))
}
sealed trait TemporalInstancesLowPriority1 {
  @inline implicit final def TemporalMonix[MonixBIO[+_, +_]: `monix.bio.IO`, Timer[_[_]]: `cats.effect.Timer`](
    implicit
    timer: Timer[MonixBIO[Nothing, _]]
  ): Predefined.Of[Temporal2[MonixBIO]] = new TemporalMonix(timer.asInstanceOf[cats.effect.Timer[monix.bio.UIO]]).asInstanceOf[Predefined.Of[Temporal2[MonixBIO]]]
}
