package izumi.functional.bio.impl

import izumi.functional.bio.Error2

import scala.util.Try

object BioEither extends BioEither

class BioEither extends Error2[Either] {
  override def pure[A](a: A): Either[Nothing, A] = Right(a)
  override def map[R, E, A, B](r: Either[E, A])(f: A => B): Either[E, B] = r.map(f)

  /** execute two operations in order, map their results */
  override def map2[R, E, A, B, C](firstOp: Either[E, A], secondOp: => Either[E, B])(f: (A, B) => C): Either[E, C] = {
    firstOp.flatMap(a => secondOp.map(b => f(a, b)))
  }
  override def flatMap[R, E, A, B](r: Either[E, A])(f: A => Either[E, B]): Either[E, B] = r.flatMap(f)

  override def catchAll[R, E, A, E2](r: Either[E, A])(f: E => Either[E2, A]): Either[E2, A] = r.left.flatMap(f)
  override def catchSome[R, E, A, E1 >: E](r: Either[E, A])(f: PartialFunction[E, Either[E1, A]]): Either[E1, A] = r.left.flatMap(f)
  override def fail[E](v: => E): Either[E, Nothing] = Left(v)

  override def fromEither[E, V](effect: => Either[E, V]): Either[E, V] = effect
  override def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): Either[E, A] = effect match {
    case Some(value) => Right(value)
    case None => Left(errorOnNone)
  }
  override def fromTry[A](effect: => Try[A]): Either[Throwable, A] = effect.toEither

  override def guarantee[R, E, A](f: Either[E, A], cleanup: Either[Nothing, Unit]): Either[E, A] = f
}
