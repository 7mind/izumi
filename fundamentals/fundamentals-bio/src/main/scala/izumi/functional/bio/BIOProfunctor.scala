package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.functional.bio.impl.BIOAsyncZio
import zio.ZIO

trait BIOProfunctor[FR[-_, +_, +_]] extends BIOProfunctorInstances with PredefinedHelper {
  val InnerF: BIOFunctor3[FR]

  def dimap[R1, E, A1, R2, A2](fra: FR[R1, E, A1])(fr: R2 => R1)(fa: A1 => A2): FR[R2, E, A2]

  // defaults
  def contramap[R, E, A, R0](fr: FR[R, E, A])(f: R0 => R): FR[R0, E, A] = dimap(fr)(f)(identity)
}

private[bio] sealed trait BIOProfunctorInstances
object BIOProfunctorInstances {
  @inline implicit final def BIOArrowChoiceZIO: Predefined.Of[BIOArrowChoice[ZIO]] = Predefined(BIOAsyncZio)
}
