package izumi.functional.bio.syntax

import izumi.functional.bio.IO2

import scala.annotation.{targetName, unused}

trait IO2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: IO2[F]) {
    @targetName("bracketAutoExt")
    def bracketAuto[E1 >: E, B](use: A => F[E1, B])(using @unused ev: A <:< AutoCloseable): F[E1, B] =
      F.bracket[E1, A, B](r.asInstanceOf[F[E1, A]])(c => F.sync(ev(c).close()))(use)
  }
}
