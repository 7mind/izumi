package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.fundamentals.orphans.`monix.bio.IO`
import zio.ZIO

trait BIOFork3[F[-_, +_, +_]] extends BIORootBifunctor[F] with BIOForkInstances {
  def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, BIOFiber3[F, E, A]]
}

private[bio] sealed trait BIOForkInstances
object BIOForkInstances extends LowPriorityBIOForkInstances {
  @inline implicit def BIOForkZio: Predefined.Of[BIOFork3[ZIO]] = Predefined(impl.BIOForkZIO)
}
sealed trait LowPriorityBIOForkInstances {
  @inline implicit def BIOForkMonix[MonixBIO[+_, +_]: `monix.bio.IO`]: Predefined.Of[BIOFork[MonixBIO]] = impl.BIOForkMonix.asInstanceOf[Predefined.Of[BIOFork[MonixBIO]]]
}
