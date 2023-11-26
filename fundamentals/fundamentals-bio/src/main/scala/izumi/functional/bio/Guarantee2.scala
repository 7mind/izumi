package izumi.functional.bio

trait Guarantee2[F[+_, +_]] extends Applicative2[F] {
  def guarantee[E, A](f: F[E, A], cleanup: F[Nothing, Unit]): F[E, A]
}
