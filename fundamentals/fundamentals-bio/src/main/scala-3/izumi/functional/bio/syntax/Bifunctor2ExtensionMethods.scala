package izumi.functional.bio.syntax

import izumi.functional.bio.Bifunctor2

import scala.annotation.{targetName, unused}

trait Bifunctor2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Bifunctor2[F]) {
    @targetName("leftMapBifunctorExt")
    def leftMap[E2](f: E => E2): F[E2, A] = F.leftMap(r)(f)

    @targetName("bimapBifunctorExt")
    def bimap[E2, B](f: E => E2, g: A => B): F[E2, B] = F.bimap(r)(f, g)

    @targetName("widenErrorBifunctorExt")
    def widenError[E1](using @unused ev: E <:< E1): F[E1, A] = r.asInstanceOf[F[E1, A]]

    @targetName("widenBothBifunctorExt")
    def widenBoth[E1, A1](using @unused ev1: E <:< E1, @unused ev2: A <:< A1): F[E1, A1] = r.asInstanceOf[F[E1, A1]]
  }
}
