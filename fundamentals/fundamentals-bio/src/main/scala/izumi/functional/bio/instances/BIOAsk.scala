package izumi.functional.bio.instances

import izumi.functional.bio.impl
import zio.{NeedsEnv, ZIO}

trait BIOAsk[FR[-_, +_, +_]] extends BIOAskInstances {
  def ask[R]: FR[R, Nothing, R]

  def access[R, A](f: R => A)(implicit ev: NeedsEnv[R]): FR[R, Nothing, A]
  def accessThrowable[R, A](f: R => A)(implicit ev: NeedsEnv[R]): FR[R, Throwable, A]
  def accessM[R, E, A](f: R => FR[R, E, A])(implicit ev: NeedsEnv[R]): FR[R, E, A]
}

private[bio] sealed trait BIOAskInstances
object BIOAskInstances {
  @inline implicit final def BIOLocalZio: BIOLocal[ZIO] = impl.BIOLocalZio
}
