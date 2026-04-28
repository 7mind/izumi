package izumi.functional.bio.syntax

import izumi.functional.bio.Applicative2

import scala.annotation.targetName

trait Applicative2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Applicative2[F]) {
    /** execute two operations in order, return result of second operation */
    @targetName("andThenExt")
    def *>[E1 >: E, B](f0: => F[E1, B]): F[E1, B] = F.*>(r, f0)

    /** execute two operations in order, same as `*>`, but return result of first operation */
    @targetName("andKeepExt")
    def <*[E1 >: E, B](f0: => F[E1, B]): F[E1, A] = F.<*(r, f0)

    /** execute two operations in order, return result of both operations */
    @targetName("zipExt")
    infix def zip[E2 >: E, B](r2: => F[E2, B]): F[E2, (A, B)] = F.zip(r, r2)

    /** execute two operations in order, map their results */
    @targetName("map2Ext")
    def map2[E2 >: E, B, C](r2: => F[E2, B])(f: (A, B) => C): F[E2, C] = F.map2(r, r2)(f)

    @targetName("foreverExt")
    def forever: F[E, Nothing] = F.forever(r)
  }
}
