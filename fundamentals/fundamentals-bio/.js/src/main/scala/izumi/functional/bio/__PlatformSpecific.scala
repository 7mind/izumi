package izumi.functional.bio

import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.CompletionStage
import scala.annotation.unused
import zio.IO
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[bio] object __PlatformSpecific {
  @inline def fromFutureJava[A](@unused javaFuture: => CompletionStage[A]): IO[Throwable, A] = {
    throw new RuntimeException("No CompletionStage on Scala.js!")
  }

  disableAutoTrace.discard()
}
