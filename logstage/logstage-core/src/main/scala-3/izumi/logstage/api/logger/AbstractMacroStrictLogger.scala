package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.api.Log.Message

trait AbstractMacroStrictLogger { this: AbstractLogger =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    */
  transparent inline final def trace(inline message: String): Unit = log(Log.Level.Trace, message)

  transparent inline final def debug(inline message: String): Unit = log(Log.Level.Debug, message)

  transparent inline final def info(inline message: String): Unit = log(Log.Level.Info, message)

  transparent inline final def warn(inline message: String): Unit = log(Log.Level.Warn, message)

  transparent inline final def error(inline message: String): Unit = log(Log.Level.Error, message)

  transparent inline final def crit(inline message: String): Unit = log(Log.Level.Crit, message)

  transparent inline final def log(inline level: Log.Level, inline message: String): Unit = {
    if (acceptable(Log.LoggerId(CodePositionMaterializer.materializeApplicationPointId), level)) {
      unsafeLog(Log.Entry.create(level, Message.strict(message))(CodePositionMaterializer.materialize))
    }
  }

}
