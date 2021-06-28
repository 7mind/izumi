package logstage.strict

import izumi.functional.bio.SyncSafe2
import izumi.functional.mono.SyncSafe
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.AbstractLogger
import logstage.strict.LogIO3AskStrict.LogIO3AskStrictImpl
import logstage.strict.LogstageCatsStrict.WrappedLogIOStrict
import zio.{IO, ZIO}

object LogZIOStrict {
  type Service = LogIO3Strict[ZIO]

  /**
    * Lets you carry LogZIOStrict capability in environment
    *
    * {{{
    *   import logstage.strict.LogZIOStrict
    *   import logstage.strict.LogZIOStrict.log
    *   import zio.URIO
    *
    *   def fn: URIO[LogZIOStrict, Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    * }}}
    */
  object log extends LogIO3AskStrictImpl[ZIO](_.get)

  def withFiberIdStrict(logger: AbstractLogger): LogIO2Strict[IO] = {
    new WrappedLogIOStrict[IO[Nothing, _]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[IO[Nothing, _]] = {
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

  def withDynamicContextStrict[R](logger: AbstractLogger)(dynamic: ZIO[R, Nothing, CustomContext]): LogIOStrict[ZIO[R, Nothing, _]] = {
    new WrappedLogIOStrict[ZIO[R, Nothing, _]](logger)(SyncSafe[ZIO[R, Nothing, _]]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[ZIO[R, Nothing, _]] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): ZIO[R, Nothing, A] = {
        dynamic.flatMap(ctx => IO.effectTotal(f(logger.withCustomContext(ctx))))
      }
    }
  }

  @deprecated("renamed to logstage.strict.LogZIOStrict", "1.0")
  type LogZIOStrict = logstage.strict.LogZIOStrict
  @deprecated("renamed to logstage.strict.LogZIOStrict", "1.0")
  object LogZIOStrict {
    @deprecated("renamed to logstage.strict.LogZIOStrict.Service", "1.0")
    type Service = logstage.strict.LogZIOStrict.Service
  }
}
