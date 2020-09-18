package izumi.functional.bio

import zio.ZIO

trait BIOFork3[F[-_, +_, +_]] extends BIOForkInstances {
  def fork[R, E, A](f: F[R, E, A]): F[R, Nothing, BIOFiber3[F, E, A]]
}

private[bio] sealed trait BIOForkInstances
object BIOForkInstances extends LowPriorityBIOForkInstances {
  @inline implicit def BIOForkZio: BIOFork3[ZIO] = impl.BIOForkZIO
}
sealed trait LowPriorityBIOForkInstances extends LowPriorityBIOForkInstances1 {
  @inline implicit def BIOForkMonix: BIOFork[monix.bio.IO] = impl.BIOForkMonix
}
sealed trait LowPriorityBIOForkInstances1 {
  @inline implicit final def BIOFork3To2[FR[-_, +_, +_], R](implicit BIOFork3: BIOFork3[FR]): BIOFork[FR[R, +?, +?]] = cast3To2(BIOFork3)
}
