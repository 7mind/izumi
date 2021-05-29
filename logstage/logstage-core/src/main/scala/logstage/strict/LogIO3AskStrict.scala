package logstage.strict

import izumi.functional.bio.MonadAsk3
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{CustomContext, Level}
import izumi.reflect.Tag
import zio.Has

object LogIO3AskStrict {
  @inline def apply[F[_, _, _]: LogIO3AskStrict]: LogIO3AskStrict[F] = implicitly

  /**
    * Lets you carry LogIO3 capability in environment
    *
    * {{{
    *   import logstage.{LogIO3Strict, LogIO3AskStrict}
    *   import logstage.LogIO3AskStrict.log
    *   import zio.{Has. ZIO}
    *
    *   class Service[F[-_, +_, +_]: LogIO3AskStrict] {
    *     val fn: F[Has[LogIO3Strict[F]], Nothing, Unit] = {
    *       log.info(s"I'm logging with ${log}stage!")
    *     }
    *   }
    *
    *   new Service[ZIO](LogIO3AskStrict.make)
    * }}}
    */
  @inline def make[F[-_, +_, +_]: MonadAsk3](implicit t: Tag[LogIO3Strict[F]]): LogIO3AskStrict[F] = new LogIO3AskStrictImpl[F](_.get)

  /**
    * Lets you carry LogIO3 capability in environment
    *
    * {{{
    *   import logstage.{LogIO3Strict, LogIO3AskStrict}
    *   import logstage.LogIO3AskStrict.log
    *   import zio.{Has. ZIO}
    *
    *   class Service[F[-_, +_, +_]: LogIO3AskStrict] {
    *     val fn: F[Has[LogIO3Strict[F]], Nothing, Unit] = {
    *       log.info(s"I'm logging with ${log}stage!")
    *     }
    *   }
    *
    *   new Service[ZIO](LogIO3AskStrict.make)
    * }}}
    */
  @inline def log[F[-_, +_, +_]](implicit l: LogIO3AskStrict[F]): l.type = l

  class LogIO3AskStrictImpl[F[-_, +_, +_]](
    get: Has[LogIO3Strict[F]] => LogIO3Strict[F]
  )(implicit
    F: MonadAsk3[F]
  ) extends LogIO3AskStrict[F] {
    override final def log(entry: Log.Entry): F[Has[LogIO3Strict[F]], Nothing, Unit] =
      F.access(get(_).log(entry))
    override final def log(logLevel: Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): F[Has[LogIO3Strict[F]], Nothing, Unit] =
      F.access(get(_).log(logLevel)(messageThunk))
    override final def unsafeLog(entry: Log.Entry): F[Has[LogIO3Strict[F]], Nothing, Unit] =
      F.access(get(_).log(entry))
    override final def acceptable(loggerId: Log.LoggerId, logLevel: Level): F[Has[LogIO3Strict[F]], Nothing, Boolean] =
      F.access(get(_).acceptable(loggerId, logLevel))
    override final def acceptable(logLevel: Level)(implicit pos: CodePositionMaterializer): F[Has[LogIO3Strict[F]], Nothing, Boolean] =
      F.access(get(_).acceptable(logLevel))
    override final def createEntry(logLevel: Level, message: Log.Message)(implicit pos: CodePositionMaterializer): F[Has[LogIO3Strict[F]], Nothing, Log.Entry] =
      F.access(get(_).createEntry(logLevel, message))
    override final def createContext(
      logLevel: Level,
      customContext: CustomContext,
    )(implicit pos: CodePositionMaterializer
    ): F[Has[LogIO3Strict[F]], Nothing, Log.Context] =
      F.access(get(_).createContext(logLevel, customContext))
    override final def withCustomContext(context: CustomContext): LogIO2Strict[F[Has[LogIO3Strict[F]], _, _]] = {
      new LogIO3AskStrictImpl[F](get(_).withCustomContext(context))
    }
  }
}
