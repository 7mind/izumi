package logstage.strict

import izumi.functional.bio.{SyncSafe1, SyncSafe2}
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractLogger, AbstractLoggerF}
import logstage.LogZIO
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

  def withFiberIdStrict(logger: AbstractLogger): LogIO2Strict[IO] = {
    new WrappedLogIOStrict[IO[Nothing, _]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[IO[Nothing, _]] = {
        withFiberIdStrict(logger.withCustomContext(context))
      }

      override protected def wrap[A](f: AbstractLogger => A): IO[Nothing, A] = {
        LogZIO.addFiberIdToLogger(logger)(logger => ZIO.succeed(f(logger)))
      }
    }
  }

  def withDynamicContextStrict[R](logger: AbstractLogger)(dynamic: ZIO[R, Nothing, CustomContext]): LogIOStrict[ZIO[R, Nothing, _]] = {
    new WrappedLogIOStrict[ZIO[R, Nothing, _]](logger)(SyncSafe1[ZIO[R, Nothing, _]]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[ZIO[R, Nothing, _]] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }

      override protected def wrap[A](f: AbstractLogger => A): ZIO[R, Nothing, A] = {
        dynamic.flatMap(dynCtx => ZIO.succeed(f(logger.withCustomContext(dynCtx))))
      }
    }
  }

  def withFiberIdStrict(logger: AbstractLoggerF[IO[Nothing, _]]): LogIO2Strict[IO] = {
    new WrappedLogIOStrictF[IO[Nothing, _]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[IO[Nothing, _]] = {
        withFiberIdStrict(logger.withCustomContext(context))
      }

      override protected def wrap[A](f: AbstractLoggerF[IO[Nothing, _]] => IO[Nothing, A]): IO[Nothing, A] = {
        LogZIO.addFiberIdToLogger(logger)(f)
      }
    }
  }

  def withDynamicContextStrict[R](logger: AbstractLoggerF[ZIO[R, Nothing, _]])(dynamic: ZIO[R, Nothing, CustomContext]): LogIOStrict[ZIO[R, Nothing, _]] = {
    new WrappedLogIOStrictF[ZIO[R, Nothing, _]](logger)(SyncSafe1[ZIO[R, Nothing, _]]) {
      override def withCustomContext(context: CustomContext): LogIOStrict[ZIO[R, Nothing, _]] = {
        withDynamicContextStrict(logger.withCustomContext(context))(dynamic)
      }

      override protected def wrap[A](f: AbstractLoggerF[ZIO[R, Nothing, _]] => ZIO[R, Nothing, A]): ZIO[R, Nothing, A] = {
        dynamic.flatMap(dynCtx => f(logger.withCustomContext(dynCtx)))
      }
    }
  }
}
