package izumi.functional.bio

trait Guarantee3[F[-_, +_, +_]] extends Applicative3[F] {
  def guarantee[R, E, A](f: F[R, E, A], cleanup: F[R, Nothing, Unit]): F[R, E, A]
}
