package izumi.functional.bio

trait BIOAsk[FR[-_, +_, +_]] extends BIORoot {
  val InnerF: BIOApplicative3[FR]
  def ask[R]: FR[R, Nothing, R]

  // defaults
  def askWith[R, A](f: R => A): FR[R, Nothing, A] = InnerF.map[R, Nothing, R, A](ask)(f)
}
