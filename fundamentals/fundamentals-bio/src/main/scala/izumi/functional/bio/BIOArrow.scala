package izumi.functional.bio

trait BIOArrow[FR[-_, +_, +_]] extends BIOProfunctor[FR] {
  def fromFunction[R, A](f: R => A): FR[R, Nothing, A]
  def andThen[R, R1, E, A](f: FR[R, E, R1], g: FR[R1, E, A]): FR[R, E, A]
  def asking[R, E, A](f: FR[R, E, A]): FR[R, E, (A, R)]
}
