package izumi.functional.bio.instances

trait BIOMonad3[F[-_, +_, +_]] extends BIOApplicative3[F] {
  def flatMap[R, E, A, R2 <: R, E2 >: E, B](r: F[R, E, A])(f: A => F[R2, E2, B]): F[R2, E2, B]
  def flatten[R, E, A](r: F[R, E, F[R, E, A]]): F[R, E, A] = flatMap(r)(identity)

  def tap[R, E, A, R2 <: R, E2 >: E](r: F[R, E, A])(f: A => F[R2, E2, Unit]): F[R2, E2, A] = flatMap[R, E, A, R2, E2, A](r)(a => as(f(a))(a))
  @inline final def when[R, E, E1](cond: F[R, E, Boolean])(ifTrue: F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
    ifThenElse(cond)(ifTrue, unit)
  }
  @inline final def unless[R, E, E1](cond: F[R, E, Boolean])(ifFalse: F[R, E1, Unit])(implicit ev: E <:< E1): F[R, E1, Unit] = {
    ifThenElse(cond)(unit, ifFalse)
  }
  @inline final def ifThenElse[R, E, E1, A](cond: F[R, E, Boolean])(ifTrue: F[R, E1, A], ifFalse: F[R, E1, A])(implicit ev: E <:< E1): F[R, E1, A] = {
    flatMap(widenError(cond)(ev))(if (_) ifTrue else ifFalse)
  }

  // defaults
  override def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B] = flatMap(r)(a => pure(f(a)))
  override def *>[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, B] = flatMap(f)(_ => next)
  override def <*[R, E, A, B](f: F[R, E, A], next: => F[R, E, B]): F[R, E, A] = flatMap(f)(a => map(next)(_ => a))
  override def map2[R, E, A, B, C](r1: F[R, E, A], r2: => F[R, E, B])(f: (A, B) => C): F[R, E, C] = flatMap(r1)(a => map(r2)(b => f(a, b)))
}
