package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Message

trait AbstractMacroRawLogIO[F[_]] { this: AbstractLogIO[F] =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  inline final def trace(inline message: String): F[Unit] = log(Log.Level.Trace, message)

  inline final def debug(inline message: String): F[Unit] = log(Log.Level.Debug, message)

  inline final def info(inline message: String): F[Unit] = log(Log.Level.Info, message)

  inline final def warn(inline message: String): F[Unit] = log(Log.Level.Warn, message)

  inline final def error(inline message: String): F[Unit] = log(Log.Level.Error, message)

  inline final def crit(inline message: String): F[Unit] = log(Log.Level.Crit, message)

  inline final def log(inline level: Log.Level, inline message: String): F[Unit] = {
    log(level)(Message.raw(message))(CodePositionMaterializer.materialize)
  }
}
