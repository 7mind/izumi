package izumi.functional.bio

import izumi.fundamentals.platform.language.unused

trait Functor3[F[-_, +_, +_]] extends RootBifunctor[F] {
  def map[R, E, A, B](r: F[R, E, A])(f: A => B): F[R, E, B]

  def as[R, E, A, B](r: F[R, E, A])(v: => B): F[R, E, B] = map(r)(_ => v)
  def void[R, E, A](r: F[R, E, A]): F[R, E, Unit] = map(r)(_ => ())
  @inline final def widen[R, E, A, A1](r: F[R, E, A])(implicit @unused ev: A <:< A1): F[R, E, A1] = r.asInstanceOf[F[R, E, A1]]
}
