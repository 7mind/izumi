package izumi.logstage.api.logger

import izumi.logstage.api.Log.Level
import izumi.logstage.macros.LoggerMacroMethods.NonStrict

import scala.language.experimental.macros

trait AbstractMacroLogger { this: AbstractLogger =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    */
  final def trace(message: String): Unit = macro NonStrict.scTraceMacro
  final def debug(message: String): Unit = macro NonStrict.scDebugMacro
  final def info(message: String): Unit = macro NonStrict.scInfoMacro
  final def warn(message: String): Unit = macro NonStrict.scWarnMacro
  final def error(message: String): Unit = macro NonStrict.scErrorMacro
  final def crit(message: String): Unit = macro NonStrict.scCritMacro

  final def logValues(level: Level)(values: Any*): Unit = macro NonStrict.scLogValues

  final def logMethod[A](level: Level)(function: => A): A = macro NonStrict.scLogMethod[A]
  final def logMethod[A](level: Level, printTypes: Boolean)(function: => A): A = macro NonStrict.scLogMethodPrintTypes[A]
  final def logMethod[A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => A): A = macro NonStrict.scLogMethodPrintTypesImplicits[A]
}
