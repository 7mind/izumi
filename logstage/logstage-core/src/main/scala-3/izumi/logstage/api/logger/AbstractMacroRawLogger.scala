package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Message


trait AbstractMacroRawLogger { this: AbstractLogger =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    */
  inline final def trace(inline message: String): Unit = log(Log.Level.Trace, message)

  inline final def debug(inline message: String): Unit = log(Log.Level.Debug, message)

  inline final def info(inline message: String): Unit = log(Log.Level.Info, message)

  inline final def warn(inline message: String): Unit = log(Log.Level.Warn, message)

  inline final def error(inline message: String): Unit = log(Log.Level.Error, message)

  inline final def crit(inline message: String): Unit = log(Log.Level.Crit, message)

  inline final def log(level: Log.Level, inline message: String): Unit = {
    if (acceptable(Log.LoggerId(CodePositionMaterializer.applicationPointId), level)) {
      unsafeLog(Log.Entry.create(level, Message.raw(message))(CodePositionMaterializer.materialize))
    }
  }
}
