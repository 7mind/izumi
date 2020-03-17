package izumi.functional.bio

trait BIOArrowChoice[FR[-_, +_, +_]] extends BIOArrow[FR] {
  def choose[R1, R2, E, A, B](f: FR[R1, E, A], g: FR[R2, E, B]): FR[Either[R1, R2], E, Either[A, B]]

  // defaults
  def choice[R1, R2, E, A](f: FR[R1, E, A], g: FR[R2, E, A]): FR[Either[R1, R2], E, A] = dimap(choose(f, g))(identity[Either[R1, R2]])(_.merge)
}
