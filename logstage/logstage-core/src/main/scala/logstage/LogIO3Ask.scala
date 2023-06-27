package logstage

import izumi.functional.bio.MonadAsk3
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.CustomContext

object LogIO3Ask {
  type Service[F[_, _, _]] = LogIO3[F]

  @inline def apply[F[_, _, _]: LogIO3Ask]: LogIO3Ask[F] = implicitly

  /**
    * Lets you carry LogIO3 capability in environment
    *
    * {{{
    *   import logstage.{LogIO3, LogIO3Ask}
    *   import logstage.LogIO3Ask.log
    *   import zio.ZIO
    *
    *   def fn[F[-_, +_, +_]: LogIO3Ask]: F[LogIO3Ask.Service[F], Unit] = {
    *     log.info(s"I'm logging with ${log}stage!")
    *   }
    *
    *   fn[ZIO]
    * }}}
    */
  @inline def log[F[-_, +_, +_]](implicit l: LogIO3Ask[F]): l.type = l

  //FIXME wtf
//  @inline def make[F[-_, +_, +_]: MonadAsk3](implicit t: Tag[LogIO3[F]]): LogIO3Ask[F] = LogIO.fromBIOMonadAsk
  @inline def make[F[-_, +_, +_]: MonadAsk3]: LogIO3Ask[F] = LogIO.fromBIOMonadAsk

  class LogIO3AskImpl[F[-_, +_, +_]](
    // FIXME wtf
//    get: Has[LogIO3[F]] => LogIO3[F]
    get: LogIO3[F] => LogIO3[F]
  )(implicit
    F: MonadAsk3[F]
  ) extends LogIO3Ask[F] {
    override final def log(entry: Log.Entry): F[LogIO3[F], Nothing, Unit] =
      F.access(get(_).log(entry))
    override final def log(logLevel: Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): F[LogIO3[F], Nothing, Unit] =
      F.access(get(_).log(logLevel)(messageThunk))
    override final def unsafeLog(entry: Log.Entry): F[LogIO3[F], Nothing, Unit] =
      F.access(get(_).log(entry))
    override final def acceptable(loggerId: Log.LoggerId, logLevel: Level): F[LogIO3[F], Nothing, Boolean] =
      F.access(get(_).acceptable(loggerId, logLevel))
    override final def acceptable(logLevel: Level)(implicit pos: CodePositionMaterializer): F[LogIO3[F], Nothing, Boolean] =
      F.access(get(_).acceptable(logLevel))
    override final def createEntry(logLevel: Level, message: Log.Message)(implicit pos: CodePositionMaterializer): F[LogIO3[F], Nothing, Log.Entry] =
      F.access(get(_).createEntry(logLevel, message))
    override final def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[LogIO3[F], Nothing, Log.Context] =
      F.access(get(_).createContext(logLevel, customContext))
    override final def withCustomContext(context: CustomContext): LogIO2[F[LogIO3[F], _, _]] = {
      new LogIO3AskImpl[F](get(_).withCustomContext(context))
    }
  }

}
