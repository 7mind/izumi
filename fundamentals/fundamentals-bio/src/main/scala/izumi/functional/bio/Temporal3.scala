package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.TemporalZio
import zio.ZIO

import scala.concurrent.duration.{Duration, FiniteDuration}

trait Temporal3[F[-_, +_, +_]] extends RootBifunctor[F] with TemporalInstances {
  def InnerF: Error3[F]

  def sleep(duration: Duration): F[Any, Nothing, Unit]

  def timeout[R, E, A](duration: Duration)(r: F[R, E, A]): F[R, E, Option[A]]

  @inline final def timeoutFail[R, E, A](duration: Duration)(e: => E, r: F[R, E, A]): F[R, E, A] = {
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
  @inline implicit final def Temporal3Zio: Predefined.Of[Temporal3[ZIO]] = Predefined(new TemporalZio)
}
sealed trait TemporalInstancesLowPriority1 {
//  @inline implicit final def TemporalMonix[MonixBIO[+_, +_]: `monix.bio.IO`, Timer[_[_]]: `cats.effect.kernel.Clock`](
//    implicit
//    timer: Timer[MonixBIO[Nothing, _]]
//  ): Predefined.Of[Temporal2[MonixBIO]] = new TemporalMonix(timer.asInstanceOf[cats.effect.kernel.Clock[monix.bio.UIO]]).asInstanceOf[Predefined.Of[Temporal2[MonixBIO]]]
}
