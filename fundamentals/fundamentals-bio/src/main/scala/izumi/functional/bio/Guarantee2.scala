package izumi.functional.bio

import izumi.functional.bio.syntax.Guarantee2ExtensionMethods

trait Guarantee2[F[+_, +_]] extends Applicative2[F] with Guarantee2ExtensionMethods {
  def guarantee[E, A](f: F[E, A], cleanup: F[Nothing, Unit]): F[E, A]
}
