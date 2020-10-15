package izumi.functional.bio

import scala.util.Try

trait ApplicativeError3[F[-_, +_, +_]] extends Guarantee3[F] with Bifunctor3[F] {
  override def InnerF: Functor3[F] = this

  def fail[E](v: => E): F[Any, E, Nothing]

  /** map errors from two operations into a new error if both fail */
  def leftMap2[R, E, A, E2, E3](firstOp: F[R, E, A], secondOp: => F[R, E2, A])(f: (E, E2) => E3): F[R, E3, A]

  /** execute second operation only if the first one fails */
  def orElse[R, E, A, E2](r: F[R, E, A], f: => F[R, E2, A]): F[R, E2, A]

  def fromEither[E, V](effect: => Either[E, V]): F[Any, E, V]
  def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[Any, E, A]
  def fromTry[A](effect: => Try[A]): F[Any, Throwable, A]
}
