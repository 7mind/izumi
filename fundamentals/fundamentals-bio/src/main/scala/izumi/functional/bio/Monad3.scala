package izumi.functional.bio

import izumi.fundamentals.platform.language.unused

trait Monad3[F[-_, +_, +_]] extends Applicative3[F] {
  def flatMap[R, E, A, B](r: F[R, E, A])(f: A => F[R, E, B]): F[R, E, B]
  def flatten[R, E, A](r: F[R, E, F[R, E, A]]): F[R, E, A] = flatMap(r)(identity)

  def tailRecM[R, E, A, B](a: A)(f: A => F[R, E, Either[A, B]]): F[R, E, B] =
    flatMap(f(a)) {
      case Left(next) => tailRecM(next)(f)
      case Right(res) => pure(res)
    }

  def tap[R, E, A](r: F[R, E, A])(f: A => F[R, E, Unit]): F[R, E, A] = flatMap(r)(a => as(f(a))(a))
  @inline final def when[R, E, E1](cond: F[R, E, Boolean])(ifTrue: F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
    ifThenElse(cond)(ifTrue, unit)
  }
  @inline final def unless[R, E, E1](cond: F[R, E, Boolean])(ifFalse: F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
    ifThenElse(cond)(unit, ifFalse)
  }
  @inline final def ifThenElse[R, E, E1, A](
    cond: F[R, E, Boolean]
  )(ifTrue: F[R, E1, A],
    ifFalse: F[R, E1, A],
  )(implicit @unused ev: E <:< E1
  ): F[R, E1, A] = {
    flatMap(cond.asInstanceOf[F[R, E1, Boolean]])(if (_) ifTrue else ifFalse)
  }

  // defaults
  override def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B] = flatMap(r)(a => pure(f(a)))
  override def *>[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, B] = flatMap(f)(_ => next)
  override def <*[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, A] = flatMap(f)(a => map(next)(_ => a))
  override def map2[R, E, A, B, C](r1: F[R, E, A], r2: => F[R, E, B])(f: (A, B) => C): F[R, E, C] = flatMap(r1)(a => map(r2)(b => f(a, b)))
}
