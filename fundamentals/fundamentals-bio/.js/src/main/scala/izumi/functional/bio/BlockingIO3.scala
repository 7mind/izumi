package izumi.functional.bio

/** Scala.js does not support blockingIO */
private[bio] trait BlockingIO3[F[_, _, _]] {
  private[bio] def shiftBlocking[R, E, A](f: F[R, E, A]): F[R, E, A]
  private[bio] def syncBlocking[A](f: => A): F[Any, Throwable, A]
  private[bio] def syncInterruptibleBlocking[A](f: => A): F[Any, Throwable, A]
}
private[bio] object BlockingIO3
