package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log.{Level, Message}
import izumi.logstage.api.{Log, LogMethodMacro}

trait AbstractMacroLogger { this: AbstractLogger =>

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

  transparent inline final def logMethod[A](inline level: Level)(inline function: => A): A = {
    ${ LogMethodMacro.logMethod('level, 'function, 'this, 'true, 'true) }
  }
  transparent inline final def logMethod[A](inline level: Level, inline logTypes: Boolean)(inline function: => A): A = {
    ${ LogMethodMacro.logMethod('level, 'function, 'this, 'logTypes, 'true) }
  }
  transparent inline final def logMethod[A](inline level: Level, inline logTypes: Boolean, inline logImplicits: Boolean)(inline function: => A): A = {
    ${ LogMethodMacro.logMethod('level, 'function, 'this, 'logTypes, 'logImplicits) }
  }

  transparent inline final def log(inline level: Log.Level, inline message: String): Unit = {
    val pos = CodePositionMaterializer.materialize
    if (acceptable(pos.get, level)) {
      unsafeLog(Log.Entry.create(level, Message(message))(pos))
    }
  }
}
