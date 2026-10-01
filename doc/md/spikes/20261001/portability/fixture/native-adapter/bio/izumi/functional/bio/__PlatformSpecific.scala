package izumi.functional.bio

import java.util.concurrent.CompletionStage
import zio.{IO, ZIO}

private[bio] object __PlatformSpecific {
  @inline private[bio] final def fromFutureJava[A](javaFuture: => CompletionStage[A]): IO[Throwable, A] = {
    ZIO.suspendSucceed {
      val stage = javaFuture
      ZIO.async[Any, Throwable, A] { callback =>
        stage.whenComplete { (value: A, failure: Throwable) =>
          callback(if (failure == null) ZIO.succeed(value) else ZIO.fail(failure))
        }
        ()
      }
    }
  }
}
