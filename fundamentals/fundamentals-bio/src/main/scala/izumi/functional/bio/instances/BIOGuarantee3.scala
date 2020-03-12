package izumi.functional.bio.instances

trait BIOGuarantee3[F[-_, +_, +_]] extends BIOApplicative3[F] {
  def guarantee[R, E, A](f: F[R, E, A])(cleanup: F[R, Nothing, Unit]): F[R, E, A]
}
