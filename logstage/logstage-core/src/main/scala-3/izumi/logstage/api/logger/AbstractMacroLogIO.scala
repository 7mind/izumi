package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.macros.{LogMessageMacro, LogValuesMacro}

trait AbstractMacroLogIO[F[_]] { this: AbstractLogIO[F] { type EncMode <: Singleton } =>

  /** Aliases for [[AbstractLogIO#log(entry:*]] that look better in Intellij */
  transparent inline final def trace(inline message: String): F[Unit] = logImpl(Log.Level.Trace, message)
  transparent inline final def debug(inline message: String): F[Unit] = logImpl(Log.Level.Debug, message)
  transparent inline final def info(inline message: String): F[Unit] = logImpl(Log.Level.Info, message)
  transparent inline final def warn(inline message: String): F[Unit] = logImpl(Log.Level.Warn, message)
  transparent inline final def error(inline message: String): F[Unit] = logImpl(Log.Level.Error, message)
  transparent inline final def crit(inline message: String): F[Unit] = logImpl(Log.Level.Crit, message)
  transparent inline final def audit(inline message: String): F[Unit] = logImpl(Log.Level.Audit, message)

  transparent inline final def traceTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Trace, message)
  transparent inline final def debugTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Debug, message)
  transparent inline final def infoTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Info, message)
  transparent inline final def warnTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Warn, message)
  transparent inline final def errorTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Error, message)
  transparent inline final def critTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Crit, message)
  transparent inline final def auditTo(sinkKey: String)(inline message: String): F[Unit] = logToImpl(sinkKey, Log.Level.Audit, message)

  transparent inline final def logValues(level: Log.Level)(inline values: Any*): F[Unit] = {
    ${ LogValuesMacro.logValuesIO[F, EncMode]('{ this }, '{ level }, '{ values }) }
  }

  // NOTE: `logMethod` / `logMethodF` removed pending M5 Session 6 rework — they relied on the deleted
  //       `IO1` / `Primitives1` typeclasses (specifically `IO1#maybeSuspend` + `Primitives1#tapBothUntyped`,
  //       neither of which has a direct BIO2 analogue). Session 6 will reintroduce them on top of
  //       a bifunctor effect (`IO2` + `Error2.tapBoth`) — for now any caller must invoke them inline.

  private[AbstractMacroLogIO] transparent inline final def logImpl(inline level: Log.Level, inline message: String): F[Unit] = {
    this.log(level)(LogMessageMacro.createMessageWithMode[EncMode](message))(CodePositionMaterializer.materialize)
  }

  private[AbstractMacroLogIO] transparent inline final def logToImpl(inline sinkKey: String, inline level: Log.Level, inline message: String): F[Unit] = {
    this.logTo(sinkKey)(level)(LogMessageMacro.createMessageWithMode[EncMode](message))(CodePositionMaterializer.materialize)
  }
}
