package izumi.logstage.api.logger

import izumi.logstage.api.Log.Level
import izumi.logstage.macros.LoggerMacroMethods.Raw

import scala.language.experimental.macros

trait AbstractMacroRawLogger { this: AbstractLogger =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    */
  final def trace(message: String): Unit = macro Raw.scTraceMacro
  final def debug(message: String): Unit = macro Raw.scDebugMacro
  final def info(message: String): Unit = macro Raw.scInfoMacro
  final def warn(message: String): Unit = macro Raw.scWarnMacro
  final def error(message: String): Unit = macro Raw.scErrorMacro
  final def crit(message: String): Unit = macro Raw.scCritMacro

  final def logValues(level: Level)(values: Any*): Unit = macro Raw.scLogValues

  final def logMethod[A](level: Level)(function: => A): A = macro Raw.scLogMethod[A]
  final def logMethod[A](level: Level, printTypes: Boolean)(function: => A): A = macro Raw.scLogMethodPrintTypes[A]
  final def logMethod[A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => A): A = macro Raw.scLogMethodPrintTypesImplicits[A]
}
