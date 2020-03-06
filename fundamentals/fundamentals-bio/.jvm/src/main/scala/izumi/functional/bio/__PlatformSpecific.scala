package izumi.functional.bio

import java.util.concurrent.CompletionStage

import zio.{IO, ZIO}

private[bio] object __PlatformSpecific {
  @inline def fromFutureJava[A](javaFuture: => CompletionStage[A]): IO[Throwable, A] =
    ZIO.fromCompletionStage(javaFuture)
}
