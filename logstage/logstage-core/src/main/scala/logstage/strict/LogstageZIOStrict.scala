package logstage.strict

import izumi.functional.bio.SyncSafe2
import izumi.functional.mono.SyncSafe
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.AbstractLogger
import logstage.strict.LogstageCatsStrict.WrappedLogIOStrict
import zio.{Has, IO, ZIO}

object LogstageZIOStrict {

  type LogZIOStrict = Has[LogBIO3Strict[ZIO]]
  object LogZIOStrict {
    type Service = LogBIO3Strict[ZIO]
  }
  /**
    * Lets you carry LogZIOStrict capability in environment
    *
    * {{{
    *   import logstage.LogstageZIO.log
    *   import zio.ZIO
    *
    *   def fn[F[-_, +_, +_]]: ZIO[LogZIOStrict, Nothing, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  object log extends LogBIOEnvStrictInstance[ZIO](_.get)

  def withFiberIdStrict(logger: AbstractLogger): LogBIOStrict[IO] = {
    new WrappedLogIOStrict[IO[Nothing, ?]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[IO[Nothing, ?]] = {
        withFiberIdStrict(logger.withCustomContext(context))
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): IO[Nothing, A] = {
        IO.descriptorWith {
          descriptor =>
            IO.effectTotal(f(logger.withCustomContext(CustomContext("fiberId" -> descriptor.id))))
        }
      }
    }
  }

  def withDynamicContextStrict[R](logger: AbstractLogger)(dynamic: ZIO[R, Nothing, CustomContext]): LogIOStrict[ZIO[R, Nothing, ?]] = {
    new WrappedLogIOStrict[ZIO[R, Nothing, ?]](logger)(SyncSafe[ZIO[R, Nothing, ?]]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[ZIO[R, Nothing, ?]] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): ZIO[R, Nothing, A] = {
        dynamic.flatMap(ctx => IO.effectTotal(f(logger.withCustomContext(ctx))))
      }
    }
  }
}
