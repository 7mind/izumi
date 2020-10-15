package izumi.distage.model.effect

import cats.effect.Effect

/** Scala.js does not support running effects synchronously */
trait QuasiEffectRunner[F[_]]
object QuasiEffectRunner {
  @inline def apply[F[_]](implicit ev: QuasiEffectRunner[F]): QuasiEffectRunner[F] = ev

  def fromCats[F[_]: Effect]: QuasiEffectRunner[F] = forAny[F]

  class BIOImpl[F[_, _]] extends QuasiEffectRunner[F[Throwable, ?]]

  implicit def forAny[F[_]]: QuasiEffectRunner[F] = new QuasiEffectRunner[F] {}
}
