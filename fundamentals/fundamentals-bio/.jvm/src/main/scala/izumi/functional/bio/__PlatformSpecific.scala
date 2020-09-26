package izumi.functional.bio

import java.util.concurrent.CompletionStage

import zio.{IO, ZIO}

private[bio] trait __PlatformSpecific {
  type BIORunner3[F[-_, +_, +_]] = BIORunner[F[Any, +?, +?]]
  object BIORunner3 {
    @inline def apply[F[-_, +_, +_]: BIORunner3]: BIORunner3[F] = implicitly
  }
}

private[bio] object __PlatformSpecific {
  @inline def fromFutureJava[A](javaFuture: => CompletionStage[A]): IO[Throwable, A] =
    ZIO.fromCompletionStage(javaFuture)
}
