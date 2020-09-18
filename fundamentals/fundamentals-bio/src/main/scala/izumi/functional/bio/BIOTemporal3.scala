package izumi.functional.bio

import izumi.functional.bio.BIOTemporalInstancesLowPriority1.{_BIOTemporalMonixBIO, _TimerMonixUIO}
import izumi.functional.bio.impl.{BIOTemporalMonix, BIOTemporalZio}
import izumi.fundamentals.platform.language.unused
import zio.ZIO

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOTemporal3[F[-_, +_, +_]] extends BIOAsync3[F] with BIOTemporalInstances {
  def sleep(duration: Duration): F[Any, Nothing, Unit]
  def timeout[R, E, A](r: F[R, E, A])(duration: Duration): F[R, E, Option[A]]
  def retryOrElse[R, E, A, E2](r: F[R, E, A])(duration: FiniteDuration, orElse: => F[R, E2, A]): F[R, E2, A]

  @inline final def repeatUntil[R, E, A](action: F[R, E, Option[A]])(tooManyAttemptsError: => E, sleep: FiniteDuration, maxAttempts: Int): F[R, E, A] = {
    def go(n: Int): F[R, E, A] = {
      flatMap(action) {
        case Some(value) =>
          pure(value)
        case None =>
          if (n <= maxAttempts) {
            *>(this.sleep(sleep), go(n + 1))
          } else {
            fail(tooManyAttemptsError)
          }
      }
    }

    go(0)
  }
}

private[bio] sealed trait BIOTemporalInstances
object BIOTemporalInstances extends BIOTemporalInstancesLowPriority1 {
  @inline implicit final def BIOTemporal3Zio(implicit clockService: zio.clock.Clock): BIOTemporal3[ZIO] = new BIOTemporalZio(clockService)
}

sealed trait BIOTemporalInstancesLowPriority1 {
  @inline implicit final def BIOTemporalMonix[BIOTemporalMonixBIO, TimerMonixUIO](
    implicit
    @unused A: _BIOTemporalMonixBIO[BIOTemporalMonixBIO],
    @unused T: _TimerMonixUIO[TimerMonixUIO],
    timer: TimerMonixUIO,
  ): BIOTemporalMonixBIO = new BIOTemporalMonix(timer.asInstanceOf[cats.effect.Timer[monix.bio.UIO]]).asInstanceOf[BIOTemporalMonixBIO]
}
object BIOTemporalInstancesLowPriority1 {
  final abstract class _BIOTemporalMonixBIO[A]
  object _BIOTemporalMonixBIO {
    @inline implicit final def get: _BIOTemporalMonixBIO[BIOTemporal[monix.bio.IO]] = null
  }
  final abstract class _TimerMonixUIO[A]
  object _TimerMonixUIO {
    @inline implicit final def get: _TimerMonixUIO[cats.effect.Timer[monix.bio.UIO]] = null
  }
}
