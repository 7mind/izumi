package logstage

import izumi.functional.bio.SyncSafe2
import izumi.functional.mono.SyncSafe
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.AbstractLogger
import logstage.LogstageCats.WrappedLogIO
import zio.{Has, IO, ZIO}

object LogstageZIO {

  type LogZIO = Has[LogBIO3[ZIO]]
  object LogZIO {
    type Service = LogBIO3[ZIO]
  }
  /**
    * Lets you carry LogZIO capability in environment
    *
    * {{{
    *   import logstage.LogstageZIO.log
    *   import zio.ZIO
    *
    *   def fn[F[-_, +_, +_]]: ZIO[LogZIO, Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  object log extends LogBIOEnvInstance[ZIO](_.get)

  def withFiberId(logger: AbstractLogger): LogBIO[IO] = {
    new WrappedLogIO[IO[Nothing, ?]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogBIO[IO] = {
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

  def withDynamicContext[R](logger: AbstractLogger)(dynamic: ZIO[R, Nothing, CustomContext]): LogBIO[ZIO[R, ?, ?]] = {
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
