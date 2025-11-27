package izumi.functional.bio

import scala.concurrent.duration.Duration

trait Temporal2[F[+_, +_]] extends WeakTemporal2[F] {
  def sleep(duration: Duration): F[Nothing, Unit]

  def timeout[E, A](duration: Duration)(r: F[E, A]): F[E, Option[A]]

  @inline final def timeoutFail[E, A](duration: Duration)(e: => E, r: F[E, A]): F[E, A] = {
    InnerF.flatMap(timeout(duration)(r))(_.fold[F[E, A]](InnerF.fail(e))(InnerF.pure))
  }
}
