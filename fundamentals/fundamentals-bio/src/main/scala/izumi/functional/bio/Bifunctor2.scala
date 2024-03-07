package izumi.functional.bio

import scala.annotation.unused

trait Bifunctor2[F[+_, +_]] extends RootBifunctor[F] {
  def InnerF: Functor2[F]

  def bimap[E, A, E2, A2](r: F[E, A])(f: E => E2, g: A => A2): F[E2, A2]
  def leftMap[E, A, E2](r: F[E, A])(f: E => E2): F[E2, A] = bimap(r)(f, identity)

  @inline final def widenError[E, A, E1](r: F[E, A])(implicit @unused ev: E <:< E1): F[E1, A] = r.asInstanceOf[F[E1, A]]
  @inline final def widenBoth[E, A, E1, A1](r: F[E, A])(implicit @unused ev: E <:< E1, @unused ev2: A <:< A1): F[E1, A1] =
    r.asInstanceOf[F[E1, A1]]
}
