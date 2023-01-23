package logstage

import izumi.functional.bio.{SyncSafe1, SyncSafe2}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.{AbstractLogger, AbstractLoggerF}
import izumi.logstage.api.rendering.AnyEncoded
import izumi.reflect.Tag
import logstage.LogstageCats.{WrappedLogIO, WrappedLogIOF}
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
  object log extends LogIO3Ask.LogIO3AskImpl[ZIO](_.get[LogIO3[ZIO]])

  def withFiberId(logger: AbstractLogger): LogIO2[IO] = {
    new WrappedLogIO[IO[Nothing, _]](logger)(SyncSafe2[IO]) {
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

  def withDynamicContext[R](logger: AbstractLogger)(dynamic: ZIO[R, Nothing, CustomContext]): LogIO2[ZIO[R, _, _]] = {
    new WrappedLogIO[ZIO[R, Nothing, _]](logger)(SyncSafe1[ZIO[R, Nothing, _]]) {
      override def withCustomContext(context: CustomContext): LogIO[ZIO[R, Nothing, _]] = {
        withDynamicContext(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): ZIO[R, Nothing, A] = {
        dynamic.flatMap(ctx => IO.effectTotal(f(logger.withCustomContext(ctx))))
      }
    }
  }

  def withFiberId(logger: AbstractLoggerF[IO[Nothing, _]]): LogIO2[IO] = {
    new WrappedLogIOF[IO[Nothing, _]](logger)(SyncSafe2[IO]) {
      override def withCustomContext(context: CustomContext): LogIO2[IO] = {
        withFiberId(logger.withCustomContext(context))
      }

      override def log(entry: Log.Entry): IO[Nothing, Unit] = logger.log(entry)

      override def log(logLevel: _root_.izumi.logstage.api.Log.Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): IO[Nothing, Unit] =
        logger.log(logLevel)(messageThunk)
    }
  }

  def withDynamicContext[R](logger: AbstractLoggerF[ZIO[R, Nothing, _]])(dynamic: ZIO[R, Nothing, CustomContext]): LogIO2[ZIO[R, _, _]] = {
    new WrappedLogIOF[ZIO[R, Nothing, _]](logger)(SyncSafe1[ZIO[R, Nothing, _]]) {
      override def withCustomContext(context: CustomContext): LogIO[ZIO[R, Nothing, _]] = {
        withDynamicContext(logger.withCustomContext(context))(dynamic)
      }

      override def log(entry: Log.Entry): ZIO[R, Nothing, Unit] = logger.log(entry)

      override def log(logLevel: _root_.izumi.logstage.api.Log.Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): ZIO[R, Nothing, Unit] =
        logger.log(logLevel)(messageThunk)
    }
  }

  /**
    * Allows to provide logging context which will be passed through the given effect via ZIO environment.
    *
    * {{{
    * import logstage.LogZIO
    * import logstage.LogZIO.log
    * import zio.ZIO
    *
    * def databaseCall(): ZIO[LogZIO, Throwable, String] = ZIO.succeed("stubbed")
    *
    * def dbLayerFunction(arg: Int): ZIO[LogZIO, Throwable, String] = {
    *   LogZIO.withCustomContext("arg" -> arg) {
    *     for {
    *       result <- databaseCall
    *       _ <- log.info(s"Database call $result") // … arg=1 Database call result=stubbed
    *     } yield result
    *   }
    * }
    * }}}
    *
    * @tparam R environment of the provided effect
    * @tparam E effect error type
    * @tparam A effect return type
    * @param context context to be provided
    * @param thunk the effect for which context will be passed
    * @return effect with the passed context
    */
  def withCustomContext[R: Tag, E, A](context: (String, AnyEncoded)*)(thunk: ZIO[R, E, A]): ZIO[R with logstage.LogZIO, E, A] = {
    withCustomContext[R, E, A](CustomContext(context: _*))(thunk)
  }

  /**
    * Allows to provide logging context which will be passed through the given effect via ZIO environment.
    *
    * {{{
    * import izumi.logstage.api.Log.CustomContext
    * import logstage.LogZIO
    * import logstage.LogZIO.log
    * import zio.ZIO
    *
    * def databaseCall(): ZIO[LogZIO, Throwable, String] = ZIO.succeed("stubbed")
    *
    * def dbLayerFunction(arg: Int): ZIO[LogZIO, Throwable, String] = {
    *   LogZIO.withCustomContext(CustomContext("arg" -> arg)) {
    *     for {
    *       result <- databaseCall
    *       _ <- log.info(s"Database call $result") // … arg=1 Database call result=stubbed
    *     } yield result
    *   }
    * }
    * }}}
    *
    * @tparam R environment of the provided effect
    * @tparam E effect error type
    * @tparam A effect return type
    * @param context context to be provided
    * @param thunk the effect for which context will be passed
    * @return effect with the passed context
    */
  def withCustomContext[R: Tag, E, A](context: CustomContext)(thunk: ZIO[R, E, A]): ZIO[R with logstage.LogZIO, E, A] = {
    thunk.updateService((logZIO: Service) => logZIO(context))
  }
}
