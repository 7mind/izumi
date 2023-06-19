package izumi.functional.bio

import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.internal.stacktracer.InteropTracer

import java.util.concurrent.CompletionStage
import zio.{IO, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[bio] object __PlatformSpecific {
  @inline private[bio] final def fromFutureJava[A](javaFuture: => CompletionStage[A]): IO[Throwable, A] = {
    val byName: () => CompletionStage[A] = () => javaFuture
    implicit val trace: zio.Trace = InteropTracer.newTrace(byName)

    ZIO.fromCompletionStage(javaFuture)
  }

  disableAutoTrace.discard()
}
