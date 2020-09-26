package izumi.distage.model.effect

import cats.effect.Effect

/** Scala.js does not support running effects synchronously */
trait DIEffectRunner[F[_]]
object DIEffectRunner {
  @inline def apply[F[_]](implicit ev: DIEffectRunner[F]): DIEffectRunner[F] = ev

  def fromCats[F[_]: Effect]: DIEffectRunner[F] = forAny[F]

  class BIOImpl[F[_, _]] extends DIEffectRunner[F[Throwable, ?]]

  implicit def forAny[F[_]]: DIEffectRunner[F] = new DIEffectRunner[F] {}
}
