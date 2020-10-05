package izumi.functional.bio

import scala.util.Try

trait IO3[F[-_, +_, +_]] extends Panic3[F] {
  final type Or[+E, +A] = F[Any, E, A]
  final type Just[+A] = F[Any, Nothing, A]

  def syncThrowable[A](effect: => A): F[Any, Throwable, A]
  def sync[A](effect: => A): F[Any, Nothing, A]
  def suspend[R, A](effect: => F[R, Throwable, A]): F[R, Throwable, A] = flatten(syncThrowable(effect))

  @inline final def apply[A](effect: => A): F[Any, Throwable, A] = syncThrowable(effect)

  // defaults
  override def fromEither[E, A](effect: => Either[E, A]): F[Any, E, A] = flatMap(sync(effect)) {
    case Left(e) => fail(e): F[Any, E, A]
    case Right(v) => pure(v): F[Any, E, A]
  }
  override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[Any, E, A] = {
    flatMap(sync(effect))(e => fromEither(e.toRight(errorOnNone)))
  }
  override def fromTry[A](effect: => Try[A]): F[Any, Throwable, A] = {
    syncThrowable(effect.get)
  }
}
