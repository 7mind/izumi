package logstage

import izumi.functional.bio.SyncSafe2
import izumi.functional.mono.SyncSafe
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.AbstractLogger
import logstage.LogstageCats.WrappedLogIO
import zio.{IO, ZIO}

object LogZIO {
  type Service = LogIO3[ZIO]

  /**
    * Lets you carry LogZIO capability in environment
    *
    * {{{
    *   import logstage.LogZIO
    *   import logstage.LogZIO.log
    *   import zio.URIO
    *
    *   def fn: URIO[LogZIO, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  object log extends LogIO3Ask.LogIO3AskImpl[ZIO](_.get)

  def withFiberId(logger: AbstractLogger): LogIO2[IO] = {
    new WrappedLogIO[IO[Nothing, ?]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIO2[IO] = {
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

  def withDynamicContext[R](logger: AbstractLogger)(dynamic: ZIO[R, Nothing, CustomContext]): LogIO2[ZIO[R, ?, ?]] = {
    new WrappedLogIO[ZIO[R, Nothing, ?]](logger)(SyncSafe[ZIO[R, Nothing, ?]]) {
      override def withCustomContext(context: CustomContext): LogIO[ZIO[R, Nothing, ?]] = {
        withDynamicContext(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): ZIO[R, Nothing, A] = {
        dynamic.flatMap(ctx => IO.effectTotal(f(logger.withCustomContext(ctx))))
      }
    }
  }

  @deprecated("renamed to logstage.LogZIO", "1.0")
  type LogZIO = logstage.LogZIO
  @deprecated("renamed to logstage.LogZIO", "1.0")
  object LogZIO {
    @deprecated("renamed to logstage.LogZIO.Service", "1.0")
    type Service = logstage.LogZIO.Service
  }
}
