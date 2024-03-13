package izumi.functional.bio

import scala.annotation.unused

trait Functor2[F[+_, +_]] extends RootBifunctor[F] {
  def map[E, A, B](r: F[E, A])(f: A => B): F[E, B]

  def as[E, A, B](r: F[E, A])(v: => B): F[E, B] = map(r)(_ => v)
  def void[E, A](r: F[E, A]): F[E, Unit] = map(r)(_ => ())

  /** Extracts the optional value, or returns the given `valueOnNone` value */
  def fromOptionOr[E, A](valueOnNone: => A, r: F[E, Option[A]]): F[E, A] = map(r)(_.getOrElse(valueOnNone))

  @inline final def widen[E, A, A1](r: F[E, A])(implicit @unused ev: A <:< A1): F[E, A1] = r.asInstanceOf[F[E, A1]]
}
