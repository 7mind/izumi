package izumi.functional.bio

import cats.data.Kleisli

trait MonadAsk3[FR[-_, +_, +_]] extends Ask3[FR] with MonadAskSyntax {
  override def InnerF: Monad3[FR]

  def access[R, E, A](f: R => FR[R, E, A]): FR[R, E, A]
}

private[bio] sealed trait MonadAskSyntax
object MonadAskSyntax {
  implicit final class KleisliSyntaxMonadAsk[FR[-_, +_, +_]](private val FR: MonadAsk3[FR]) extends AnyVal {
    @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, ?], R, A]): FR[R, E, A] = FR.access(k.run)
  }
}
