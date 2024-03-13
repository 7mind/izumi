package izumi.functional.bio

import scala.util.Try

trait ApplicativeError2[F[+_, +_]] extends Guarantee2[F] with Bifunctor2[F] {
  override def InnerF: Functor2[F] = this

  def fail[E](v: => E): F[E, Nothing]

  /** map errors from two operations into a new error if both fail */
  def leftMap2[E, A, E2, E3](firstOp: F[E, A], secondOp: => F[E2, A])(f: (E, E2) => E3): F[E3, A]

  /** execute second operation only if the first one fails */
  def orElse[E, A, E2](r: F[E, A], f: => F[E2, A]): F[E2, A]

  // from* ops must suspend `effect`
  def fromEither[E, V](effect: => Either[E, V]): F[E, V]
  def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[E, A]
  def fromTry[A](effect: => Try[A]): F[Throwable, A]
}
