package izumi.functional.bio

trait Ask3[FR[-_, +_, +_]] extends RootTrifunctor[FR] {
  def InnerF: Applicative3[FR]
  def ask[R]: FR[R, Nothing, R]

  // defaults
  def askWith[R, A](f: R => A): FR[R, Nothing, A] = InnerF.map[R, Nothing, R, A](ask)(f)
}
