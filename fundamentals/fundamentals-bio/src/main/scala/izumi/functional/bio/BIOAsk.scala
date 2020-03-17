package izumi.functional.bio

import zio.ZIO

trait BIOAsk[FR[-_, +_, +_]] extends BIOAskInstances {
  val InnerF: BIOApplicative3[FR]
  def ask[R]: FR[R, Nothing, R]

  // defaults
  def askWith[R, A](f: R => A): FR[R, Nothing, A] = InnerF.map[R, Nothing, R, A](ask)(f)
}

private[bio] sealed trait BIOAskInstances
object BIOAskInstances {
  @inline implicit final def BIOLocalZio: BIOArrow[ZIO] = impl.BIOArrowZio
}
