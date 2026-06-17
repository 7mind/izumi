package izumi.functional.bio.syntax

import izumi.functional.bio.Guarantee2

import scala.annotation.targetName

trait Guarantee2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Guarantee2[F]) {
    @targetName("guaranteeExt")
    def guarantee(cleanup: F[Nothing, Unit]): F[E, A] = F.guarantee(r, cleanup)
  }
}
