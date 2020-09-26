package izumi.functional.bio

import izumi.functional.bio.impl.BIOPrimitivesCats
import izumi.fundamentals.orphans.`zio.ZIO`

trait BIOPrimitives[F[+_, +_]] extends BIOPrimitivesInstances {
  def mkRef[A](a: A): F[Nothing, BIORef[F, A]]
  def mkPromise[E, A]: F[Nothing, BIOPromise[F, E, A]]
  def mkSemaphore(permits: Long): F[Nothing, BIOSemaphore[F]]
  def mkLatch: F[Nothing, BIOPromise[F, Nothing, Unit]] = mkPromise[Nothing, Unit]
}
object BIOPrimitives {
  def apply[F[+_, +_]: BIOPrimitives]: BIOPrimitives[F] = implicitly
}

private[bio] sealed trait BIOPrimitivesInstances
object BIOPrimitivesInstances extends BIOPrimitivesLowPriorityInstances {
  @inline implicit def BIOPrimitivesZio[F[-_, +_, +_]: `zio.ZIO`]: BIOPrimitives3[F] = impl.BIOPrimitivesZio.asInstanceOf[BIOPrimitives3[F]]
}

sealed trait BIOPrimitivesLowPriorityInstances {
  @inline implicit def BIOPrimitivesFromCatsPrimitives[F[+_, +_]: BIOAsync: BIOFork]: BIOPrimitives[F] = new BIOPrimitivesCats[F]
}
