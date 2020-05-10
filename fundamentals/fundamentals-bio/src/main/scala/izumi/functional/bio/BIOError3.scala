package izumi.functional.bio

import scala.util.Try

trait BIOError3[F[-_, +_, +_]] extends BIOGuarantee3[F] with BIOBifunctor3[F] {
  override val InnerF: BIOFunctor3[F] = this

  def fail[E](v: => E): F[Any, E, Nothing]
  def catchAll[R, E, A, E2](r: F[R, E, A])(f: E => F[R, E2, A]): F[R, E2, A]
  def catchSome[R, E, A, E1 >: E](r: F[R, E, A])(f: PartialFunction[E, F[R, E1, A]]): F[R, E1, A]

  def fromEither[E, V](effect: => Either[E, V]): F[Any, E, V]
  def fromOption[E, A](errorOnNone: => E)(effect: => Option[A]): F[Any, E, A]
  def fromTry[A](effect: => Try[A]): F[Any, Throwable, A]

  def redeemPure[R, E, A, B](r: F[R, E, A])(err: E => B, succ: A => B): F[R, Nothing, B] = catchAll(map(r)(succ))(e => pure(err(e)))
  def tapError[R, E, A, E1 >: E](r: F[R, E, A])(f: E => F[R, E1, Unit]): F[R, E1, A] = catchAll(r)(e => *>(f(e), fail(e)))
  def attempt[R, E, A](r: F[R, E, A]): F[R, Nothing, Either[E, A]] = redeemPure(r)(Left(_), Right(_))

  // defaults
  override def bimap[R, E, A, E2, B](r: F[R, E, A])(f: E => E2, g: A => B): F[R, E2, B] = catchAll(map(r)(g))(e => fail(f(e)))
}
