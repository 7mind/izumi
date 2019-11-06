package logstage

import izumi.functional.bio.SyncSafe2
import izumi.functional.mono.SyncSafe
import izumi.logstage.api.AbstractLogger
import izumi.logstage.api.Log.CustomContext
import logstage.LogstageCats.WrappedLogIO
import zio.{IO, ZIO}

object LogstageZIO {

  def withFiberId(logger: AbstractLogger): LogBIO[IO] = {
    new WrappedLogIO[IO[Nothing, ?]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIO[IO[Nothing, ?]] = {
        withFiberId(logger.withCustomContext(context))
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): IO[Nothing, A] = {
        IO.descriptorWith {
          descriptor =>
            IO.effectTotal(f(logger.withCustomContext(CustomContext("fiberId" -> descriptor.id))))
        }
      }
    }
  }

  def withDynamicContext[R](logger: AbstractLogger)(dynamic: ZIO[R, Nothing, CustomContext]): LogIO[ZIO[R, Nothing, ?]] = {
    new WrappedLogIO[ZIO[R, Nothing, ?]](logger)(SyncSafe[ZIO[R, Nothing, ?]]) {
      override def withCustomContext(context: CustomContext): LogIO[ZIO[R, Nothing, ?]] = {
        withDynamicContext(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): ZIO[R, Nothing, A] = {
        dynamic.flatMap(ctx => IO.effectTotal(f(logger.withCustomContext(ctx))))
      }
    }
  }

}
