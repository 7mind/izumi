package izumi.functional.bio

import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.functional.bio.PredefinedHelper.Predefined

/** Scala.js does not support blockingIO */
trait BlockingIO3[F[_, _, _]] extends BlockingIOInstances with DivergenceHelper with PredefinedHelper {
  def shiftBlocking[R, E, A](f: F[R, E, A]): F[R, E, A]
  def syncBlocking[A](f: => A): F[Any, Throwable, A]
  def syncInterruptibleBlocking[A](f: => A): F[Any, Throwable, A]
}
object BlockingIO3 {
  def apply[F[-_, +_, +_]: BlockingIO2]: BlockingIO2[F] = implicitly
}

private[bio] sealed trait BlockingIOInstances
object BlockingIOInstances extends LowPriorityBlockingIOInstances {

  implicit def fromSyncSafe3[F[-_, +_, +_]: SyncSafe3]: Predefined.Of[BlockingIO2[F]] = Predefined(new BlockingIO2[F] {
    override def shiftBlocking[R, E, A](f: F[R, E, A]): F[R, E, A] = f
    override def syncBlocking[A](f: => A): F[Any, Throwable, A] = SyncSafe3[F].syncSafe(f)
    override def syncInterruptibleBlocking[A](f: => A): F[Any, Throwable, A] = SyncSafe3[F].syncSafe(f)
  })

  def BlockingZIODefault: BlockingIO2[zio.ZIO] = fromSyncSafe3[zio.ZIO]

}

sealed trait LowPriorityBlockingIOInstances {

  @inline implicit final def blockingConvert3To2[C[f[-_, +_, +_]] <: BlockingIO2[f], FR[-_, +_, +_], R](
    implicit BlockingIO3: C[FR] { type Divergence = Nondivergent }
  ): Divergent.Of[BlockingIO2[FR[R, +_, +_]]] = {
    Divergent(BlockingIO3.asInstanceOf[BlockingIO2[FR[R, +_, +_]]])
  }

}
