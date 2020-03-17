package izumi.functional.bio

import cats.data.Kleisli
import zio.ZIO

trait BIOAsk[FR[-_, +_, +_]] extends BIOAskInstances {
  def ask[R]: FR[R, Nothing, R]

  @inline final def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, ?], R, A]): FR[R, E, A] = accessM(k.run)

  def access[R, A](f: R => A): FR[R, Nothing, A]
  def accessThrowable[R, A](f: R => A): FR[R, Throwable, A]
  def accessM[R, E, A](f: R => FR[R, E, A]): FR[R, E, A]
}

private[bio] sealed trait BIOAskInstances
object BIOAskInstances {
  @inline implicit final def BIOLocalZio: BIOLocal[ZIO] = impl.BIOLocalZio
}
