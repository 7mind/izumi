package izumi.functional.bio

/** Scala.js does not support blockingIO */
trait BlockingIO3[F[_, _, _]] {
  private[bio] def shiftBlocking[R, E, A](f: F[R, E, A]): F[R, E, A]
  private[bio] def syncBlocking[A](f: => A): F[Any, Throwable, A]
  private[bio] def syncInterruptibleBlocking[A](f: => A): F[Any, Throwable, A]
}
object BlockingIO3 {
  implicit def fromSyncSafe3[F[-_, +_, +_]: SyncSafe3]: BlockingIO3[F] = new BlockingIO3[F] {
    override private[bio] def shiftBlocking[R, E, A](f: F[R, E, A]): F[R, E, A] = f
    override private[bio] def syncBlocking[A](f: => A): F[Any, Throwable, A] = SyncSafe3[F].syncSafe(f)
    override private[bio] def syncInterruptibleBlocking[A](f: => A): F[Any, Throwable, A] = SyncSafe3[F].syncSafe(f)
  }
}
