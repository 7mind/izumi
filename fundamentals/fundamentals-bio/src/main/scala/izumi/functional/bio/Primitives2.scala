package izumi.functional.bio

import izumi.functional.bio.impl.PrimitivesFromBIOAndCats
import izumi.fundamentals.orphans.`zio.ZIO`

trait Primitives2[F[+_, +_]] extends PrimitivesInstances {
  def mkRef[A](a: A): F[Nothing, Ref2[F, A]]
  def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]]
  def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]]
  def mkLatch: F[Nothing, Promise2[F, Nothing, Unit]] = mkPromise[Nothing, Unit]
}
object Primitives2 {
  def apply[F[+_, +_]: Primitives2]: Primitives2[F] = implicitly
}

private[bio] sealed trait PrimitivesInstances
object PrimitivesInstances extends PrimitivesLowPriorityInstances {
  @inline implicit def PrimitivesZio[F[-_, +_, +_]: `zio.ZIO`]: Primitives3[F] = impl.PrimitivesZio.asInstanceOf[Primitives3[F]]
}

sealed trait PrimitivesLowPriorityInstances {
  @inline implicit def PrimitivesFromCatsPrimitives[F[+_, +_]: Async2: Fork2]: Primitives2[F] = new PrimitivesFromBIOAndCats[F]
}
