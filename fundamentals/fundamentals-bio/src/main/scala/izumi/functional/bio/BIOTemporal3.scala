package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.{BIOTemporalMonix, BIOTemporalZio}
import monix.bio
import zio.ZIO

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOTemporal3[F[-_, +_, +_]] extends BIORootBifunctor[F] with BIOTemporalInstances {
  def InnerF: BIOError3[F]

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

private[bio] sealed trait BIOTemporalInstances
object BIOTemporalInstances extends BIOTemporalInstancesLowPriority1 {
  @inline implicit final def BIOTemporal3Zio(implicit clockService: zio.clock.Clock): Predefined.Of[BIOTemporal3[ZIO]] = Predefined(new BIOTemporalZio(clockService))
}
sealed trait BIOTemporalInstancesLowPriority1 {
  @inline implicit final def BIOTemporalMonix(implicit clock: cats.effect.Timer[bio.UIO]): Predefined.Of[BIOTemporal[bio.IO]] = Predefined(new BIOTemporalMonix(clock))
}
