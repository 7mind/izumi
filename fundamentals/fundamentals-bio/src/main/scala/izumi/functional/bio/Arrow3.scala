package izumi.functional.bio

trait Arrow3[FR[-_, +_, +_]] extends Profunctor3[FR] {
  def askWith[R, A](f: R => A): FR[R, Nothing, A]
  def andThen[R, R1, E, A](f: FR[R, E, R1], g: FR[R1, E, A]): FR[R, E, A]
  def asking[R, E, A](f: FR[R, E, A]): FR[R, E, (A, R)]
}
