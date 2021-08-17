package logstage

import izumi.functional.bio.SyncSafe2
import izumi.functional.mono.SyncSafe
import izumi.logstage.api.Log.CustomContext
import izumi.logstage.api.logger.AbstractLogger
import izumi.logstage.api.rendering.AnyEncoded
import izumi.reflect.Tag
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
    new WrappedLogIO[ZIO[R, Nothing, _]](logger)(SyncSafe[ZIO[R, Nothing, _]]) {
      override def withCustomContext(context: CustomContext): LogIO[ZIO[R, Nothing, _]] = {
        withDynamicContext(logger.withCustomContext(context))(dynamic)
      }

      override protected[this] def wrap[A](f: AbstractLogger => A): ZIO[R, Nothing, A] = {
        dynamic.flatMap(ctx => IO.effectTotal(f(logger.withCustomContext(ctx))))
      }
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

  @deprecated("renamed to logstage.LogZIO", "1.0")
  type LogZIO = logstage.LogZIO
  @deprecated("renamed to logstage.LogZIO", "1.0")
  object LogZIO {
    @deprecated("renamed to logstage.LogZIO.Service", "1.0")
    type Service = logstage.LogZIO.Service
  }
}
