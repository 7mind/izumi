package logstage.strict

import izumi.functional.bio.{SyncSafe1, SyncSafe2}
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractLogger, AbstractLoggerF}
import logstage.strict.LogstageCatsStrict.{WrappedLogIOStrict, WrappedLogIOStrictF}
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
  // FIXME wtf
//  object log extends LogIO3AskStrictImpl[ZIO](_.get[LogIO3Strict[ZIO]])
//  object log extends LogIO3AskStrictImpl[ZIO](identity)

  def withFiberIdStrict(logger: AbstractLogger): LogIO2Strict[IO] = {
    new WrappedLogIOStrict[IO[Nothing, _]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[IO[Nothing, _]] = {
        withFiberIdStrict(logger.withCustomContext(context))
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): IO[Nothing, A] = {
        ZIO.descriptorWith {
          descriptor =>
            ZIO.succeed(f(logger.withCustomContext(CustomContext("fiberId" -> descriptor.id))))
        }
      }
    }
  }

  def withFiberIdStrict(logger: AbstractLoggerF[IO[Nothing, _]]): LogIO2Strict[IO] = {
    new WrappedLogIOStrictF[IO[Nothing, _]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[IO[Nothing, _]] = {
        withFiberIdStrict(logger.withCustomContext(context))
      }
    }
  }

  def withDynamicContextStrict[R](logger: AbstractLogger)(dynamic: ZIO[R, Nothing, CustomContext]): LogIOStrict[ZIO[R, Nothing, _]] = {
    new WrappedLogIOStrict[ZIO[R, Nothing, _]](logger)(SyncSafe1[ZIO[R, Nothing, _]]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[ZIO[R, Nothing, _]] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): ZIO[R, Nothing, A] = {
        dynamic.flatMap(ctx => ZIO.succeed(f(logger.withCustomContext(ctx))))
      }
    }
  }

  def withDynamicContextStrict[R](logger: AbstractLoggerF[ZIO[R, Nothing, _]])(dynamic: ZIO[R, Nothing, CustomContext]): LogIOStrict[ZIO[R, Nothing, _]] = {
    new WrappedLogIOStrictF[ZIO[R, Nothing, _]](logger)(SyncSafe1[ZIO[R, Nothing, _]]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[ZIO[R, Nothing, _]] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }
    }
  }
}
