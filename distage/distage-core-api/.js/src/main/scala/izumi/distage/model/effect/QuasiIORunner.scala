package izumi.distage.model.effect

import scala.annotation.unused

/** Scala.js does not support running effects synchronously */
trait QuasiIORunner[F[_]]
object QuasiIORunner {
  @inline def apply[F[_]](implicit ev: QuasiIORunner[F]): QuasiIORunner[F] = ev

  def mkFromCatsDispatcher[F[_]](@unused dispatcher: cats.effect.std.Dispatcher[F]): QuasiIORunner[F] = forAny[F]
  def mkFromCatsIORuntime(@unused ioRuntime: cats.effect.unsafe.IORuntime): QuasiIORunner[cats.effect.IO] = forAny[cats.effect.IO]

  class BIOImpl[F[_, _]] extends QuasiIORunner[F[Throwable, _]]

  implicit def forAny[F[_]]: QuasiIORunner[F] = new QuasiIORunner[F] {}
}
