package izumi.distage.model.effect

import cats.effect.Effect

/** Scala.js does not support running effects synchronously */
trait QuasiIORunner[F[_]]
object QuasiIORunner {
  @inline def apply[F[_]](implicit ev: QuasiIORunner[F]): QuasiIORunner[F] = ev

  def fromCats[F[_]: Effect]: QuasiIORunner[F] = forAny[F]

  class BIOImpl[F[_, _]] extends QuasiIORunner[F[Throwable, ?]]

  implicit def forAny[F[_]]: QuasiIORunner[F] = new QuasiIORunner[F] {}
}
