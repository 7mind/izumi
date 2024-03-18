package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.TemporalZio
import izumi.fundamentals.orphans.`zio.ZIO`

import scala.concurrent.duration.{Duration, FiniteDuration}

trait Temporal2[F[+_, +_]] extends RootBifunctor[F] with TemporalInstances {
  def InnerF: Error2[F]

  def sleep(duration: Duration): F[Nothing, Unit]

  def timeout[E, A](duration: Duration)(r: F[E, A]): F[E, Option[A]]

  @inline final def timeoutFail[E, A](duration: Duration)(e: => E, r: F[E, A]): F[E, A] = {
    InnerF.flatMap(timeout(duration)(r))(_.fold[F[E, A]](InnerF.fail(e))(InnerF.pure))
  }

  @inline final def repeatUntil[E, A](action: F[E, Option[A]])(tooManyAttemptsError: => E, sleep: FiniteDuration, maxAttempts: Int): F[E, A] = {
    def go(n: Int): F[E, A] = {
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
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have zio-core as a dependency without REQUIRING a zio-core dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  @inline implicit final def Temporal2Zio[ZIO[-_, +_, +_]: `zio.ZIO`]: Predefined.Of[Temporal2[ZIO[Any, +_, +_]]] =
    Predefined(TemporalZio.asInstanceOf[Temporal2[ZIO[Any, +_, +_]]])
}
sealed trait TemporalInstancesLowPriority1 {
  @inline implicit final def Temporal2ZioR[ZIO[-_, +_, +_]: `zio.ZIO`, R]: Predefined.Of[Temporal2[ZIO[R, +_, +_]]] =
    Predefined(TemporalZio.asInstanceOf[Temporal2[ZIO[R, +_, +_]]])

//  @inline implicit final def TemporalMonix[MonixBIO[+_, +_]: `monix.bio.IO`, Timer[_[_]]: `cats.effect.kernel.Clock`](
//    implicit
//    timer: Timer[MonixBIO[Nothing, _]]
//  ): Predefined.Of[Temporal2[MonixBIO]] = new TemporalMonix(timer.asInstanceOf[cats.effect.kernel.Clock[monix.bio.UIO]]).asInstanceOf[Predefined.Of[Temporal2[MonixBIO]]]
}
