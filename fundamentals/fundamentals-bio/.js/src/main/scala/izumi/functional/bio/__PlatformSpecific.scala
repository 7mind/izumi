package izumi.functional.bio

import java.util.concurrent.CompletionStage

import zio.IO

private[bio] object __PlatformSpecific {
  @inline def fromFutureJava[A](@deprecated("x", "x") javaFuture: => CompletionStage[A]): IO[Throwable, A] = {
    throw new RuntimeException("No CompletionStage on Scala.js!")
  }
}
