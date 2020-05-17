package izumi.functional.bio

import java.util.concurrent.CompletionStage

import izumi.fundamentals.platform.language.unused
import zio.IO

private[bio] object __PlatformSpecific {
  @inline def fromFutureJava[A](@unused javaFuture: => CompletionStage[A]): IO[Throwable, A] = {
    throw new RuntimeException("No CompletionStage on Scala.js!")
  }
}
