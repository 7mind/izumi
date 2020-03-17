package izumi.functional.bio

import zio.ZIO

trait BIOAsk[FR[-_, +_, +_]] extends BIOAskInstances {
  val InnerF: BIOApplicative3[FR]
  def ask[R]: FR[R, Nothing, R]

  def access[R, A](f: R => A): FR[R, Nothing, A]
  def accessThrowable[R, A](f: R => A): FR[R, Throwable, A]
}

private[bio] sealed trait BIOAskInstances
object BIOAskInstances {
  @inline implicit final def BIOLocalZio: BIOLocal[ZIO] = impl.BIOLocalZio
}
