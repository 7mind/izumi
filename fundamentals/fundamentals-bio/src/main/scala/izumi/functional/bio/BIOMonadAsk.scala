package izumi.functional.bio

import cats.data.Kleisli

trait BIOMonadAsk[FR[-_, +_, +_]] extends BIOAsk[FR] with BIOMonadAskSyntax {
  override val InnerF: BIOMonad3[FR]
  def accessM[R, E, A](f: R => FR[R, E, A]): FR[R, E, A]
}

private[bio] sealed trait BIOMonadAskSyntax
object BIOMonadAskSyntax {
  implicit final class KleisliSyntaxMonadAsk[FR[-_, +_, +_]](private val FR: BIOMonadAsk[FR]) extends AnyVal {
    @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, ?], R, A]): FR[R, E, A] = FR.accessM(k.run)
  }
}
