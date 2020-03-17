package izumi.functional.bio

import cats.data.Kleisli

trait BIOLocal[FR[-_, +_, +_]] extends BIOMonadAsk[FR] with BIOLocalInstances {
  def provide[R, E, A](fr: FR[R, E, A])(env: => R): FR[Any, E, A] = contramap(fr)((_: Any) => env)
  def contramap[R, E, A, R0](fr: FR[R, E, A])(f: R0 => R): FR[R0, E, A]
}

private[bio] sealed trait BIOLocalInstances
object BIOLocalInstances {
  implicit final class ToKleisliSyntaxLocal[FR[-_, +_, +_]](private val FR: BIOLocal[FR]) extends AnyVal {
    @inline final def toKleisli[R, E, A](fr: FR[R, E, A]): Kleisli[FR[Any, E, ?], R, A] = Kleisli(FR.provide(fr)(_))
  }
}
