package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.Predefined
import izumi.fundamentals.orphans.`zio.ZIO`

/** Scala.js does not support blockingIO */
trait BlockingIO2[F[_, _]] extends BlockingIOInstances with DivergenceHelper with PredefinedHelper {
  def shiftBlocking[E, A](f: F[E, A]): F[E, A]
  def syncBlocking[A](f: => A): F[Throwable, A]
  def syncInterruptibleBlocking[A](f: => A): F[Throwable, A]
}
object BlockingIO2 {
  def apply[F[_, _]: BlockingIO2]: BlockingIO2[F] = implicitly
}

private[bio] sealed trait BlockingIOInstances
object BlockingIOInstances {

  implicit def fromSyncSafe2[F[+_, +_]: SyncSafe2]: Predefined.Of[BlockingIO2[F]] = Predefined(new BlockingIO2[F] {
    override def shiftBlocking[E, A](f: F[E, A]): F[E, A] = f
    override def syncBlocking[A](f: => A): F[Throwable, A] = SyncSafe2[F].syncSafe(f)
    override def syncInterruptibleBlocking[A](f: => A): F[Throwable, A] = SyncSafe2[F].syncSafe(f)
  })

  def BlockingZIODefault: BlockingIO2[zio.IO] = fromSyncSafe2[zio.IO]

  def BlockingZIODefaultR[F[-_, +_, +_]: `zio.ZIO`, R]: BlockingIO2[F[R, +_, +_]] =
    fromSyncSafe2[zio.ZIO[R, +_, +_]].asInstanceOf[BlockingIO2[F[R, +_, +_]]]

}
