package logstage

import izumi.functional.bio.BIOMonadAsk
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.CustomContext
import zio.Has

class LogBIOEnvInstance[F[-_, +_, +_]](
  get: Has[LogBIO3[F]] => LogBIO3[F]
)(implicit
  F: BIOMonadAsk[F]
) extends LogBIOEnv[F] {
  override def log(entry: Log.Entry): F[Has[LogBIO3[F]], Nothing, Unit] =
    F.access(get(_).log(entry))
  override def log(logLevel: Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): F[Has[LogBIO3[F]], Nothing, Unit] =
    F.access(get(_).log(logLevel)(messageThunk))
  override def unsafeLog(entry: Log.Entry): F[Has[LogBIO3[F]], Nothing, Unit] =
    F.access(get(_).log(entry))
  override def acceptable(loggerId: Log.LoggerId, logLevel: Level): F[Has[LogBIO3[F]], Nothing, Boolean] =
    F.access(get(_).acceptable(loggerId, logLevel))
  override def acceptable(logLevel: Level)(implicit pos: CodePositionMaterializer): F[Has[LogBIO3[F]], Nothing, Boolean] =
    F.access(get(_).acceptable(logLevel))
  override def createEntry(logLevel: Level, message: Log.Message)(implicit pos: CodePositionMaterializer): F[Has[LogBIO3[F]], Nothing, Log.Entry] =
    F.access(get(_).createEntry(logLevel, message))
  override def createContext(logLevel: Level, customContext: CustomContext)(implicit pos: CodePositionMaterializer): F[Has[LogBIO3[F]], Nothing, Log.Context] =
    F.access(get(_).createContext(logLevel, customContext))
  override def withCustomContext(context: CustomContext): LogBIO[F[Has[LogBIO3[F]], ?, ?]] = {
    new LogBIOEnvInstance[F](get(_).withCustomContext(context))
  }
}
