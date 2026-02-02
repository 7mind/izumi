package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.Level
import izumi.logstage.api.Log
import izumi.logstage.macros.{LogMessageMacro, LogMethodMacro, LogValuesMacro}

trait AbstractMacroLogger { this: AbstractLogger { type EncMode <: Singleton } =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    */
  transparent inline final def trace(inline message: String): Unit = logImpl(Log.Level.Trace, message)
  transparent inline final def debug(inline message: String): Unit = logImpl(Log.Level.Debug, message)
  transparent inline final def info(inline message: String): Unit = logImpl(Log.Level.Info, message)
  transparent inline final def warn(inline message: String): Unit = logImpl(Log.Level.Warn, message)
  transparent inline final def error(inline message: String): Unit = logImpl(Log.Level.Error, message)
  transparent inline final def crit(inline message: String): Unit = logImpl(Log.Level.Crit, message)
  transparent inline final def audit(inline message: String): Unit = logImpl(Log.Level.Audit, message)

  transparent inline final def traceTo(sinkKey: String)(inline message: String): Unit = logToImpl(sinkKey, Log.Level.Trace, message)
  transparent inline final def debugTo(sinkKey: String)(inline message: String): Unit = logToImpl(sinkKey, Log.Level.Debug, message)
  transparent inline final def infoTo(sinkKey: String)(inline message: String): Unit = logToImpl(sinkKey, Log.Level.Info, message)
  transparent inline final def warnTo(sinkKey: String)(inline message: String): Unit = logToImpl(sinkKey, Log.Level.Warn, message)
  transparent inline final def errorTo(sinkKey: String)(inline message: String): Unit = logToImpl(sinkKey, Log.Level.Error, message)
  transparent inline final def critTo(sinkKey: String)(inline message: String): Unit = logToImpl(sinkKey, Log.Level.Crit, message)
  transparent inline final def auditTo(sinkKey: String)(inline message: String): Unit = logToImpl(sinkKey, Log.Level.Audit, message)

  transparent inline final def logValues(level: Log.Level)(inline values: Any*): Unit = {
    ${ LogValuesMacro.logValues[EncMode]('{ this }, '{ level }, '{ values }) }
  }

  transparent inline final def logMethod[A](level: Level, printTypes: Boolean = false, printImplicits: Boolean = false)(inline function: => A): A = {
    ${ LogMethodMacro.logMethod[A, EncMode]('{ level }, '{ function }, '{ this }, '{ printTypes }, '{ printImplicits }) }
  }

  private[AbstractMacroLogger] transparent inline final def logImpl(inline level: Log.Level, inline message: String): Unit = {
    val pos = CodePositionMaterializer.materialize
    if (acceptable(pos.get, level)) {
      unsafeLog(
        Log.Entry.create(level, LogMessageMacro.createMessageWithMode[EncMode](message))(pos)
      )
    }
  }

  private[AbstractMacroLogger] transparent inline final def logToImpl(inline sinkKey: String, inline level: Log.Level, inline message: String): Unit = {
    val pos = CodePositionMaterializer.materialize
    if (acceptable(pos.get, level)) {
      val ctx = Log.Context.recordContext(level, Log.CustomContext.empty, Some(sinkKey))(pos)
      unsafeLog(Log.Entry(LogMessageMacro.createMessageWithMode[EncMode](message), ctx))
    }
  }
}
