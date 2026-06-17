package izumi.functional.bio

import izumi.functional.bio.syntax.Temporal2ExtensionMethods

import scala.concurrent.duration.Duration

trait Temporal2[F[+_, +_]] extends WeakTemporal2[F] with Temporal2ExtensionMethods {
  def sleep(duration: Duration): F[Nothing, Unit]

  def timeout[E, A](duration: Duration)(r: F[E, A]): F[E, Option[A]]

  @inline final def timeoutFail[E, A](duration: Duration)(e: => E, r: F[E, A]): F[E, A] = {
    InnerF.flatMap(timeout(duration)(r))(_.fold[F[E, A]](InnerF.fail(e))(InnerF.pure))
  }
}
