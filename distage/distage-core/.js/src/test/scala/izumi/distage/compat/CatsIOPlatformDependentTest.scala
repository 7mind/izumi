package izumi.distage.compat

import cats.effect.IO

trait CatsIOPlatformDependentTest {
  // FIXME without a working unsafeRunSync we can't run cats support tests on JS...
  protected[this] def catsIOUnsafeRunSync[A](io: IO[A]): A = {
    io.syncStep.attempt.unsafeRunSync() match {
      case Left(t) => throw t
      case Right(Left(_)) => throw new RuntimeException("Failed to evaluate IO synchronously")
      case Right(Right(a)) => a
    }
  }
}
