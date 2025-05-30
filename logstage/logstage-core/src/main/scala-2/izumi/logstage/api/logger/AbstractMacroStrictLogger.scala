package izumi.logstage.api.logger

import izumi.logstage.api.Log.Level
import izumi.logstage.macros.LoggerMacroMethods.Strict

import scala.language.experimental.macros

trait AbstractMacroStrictLogger { this: AbstractLogger =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    */
  final def trace(message: String): Unit = macro Strict.scTraceMacro
  final def debug(message: String): Unit = macro Strict.scDebugMacro
  final def info(message: String): Unit = macro Strict.scInfoMacro
  final def warn(message: String): Unit = macro Strict.scWarnMacro
  final def error(message: String): Unit = macro Strict.scErrorMacro
  final def crit(message: String): Unit = macro Strict.scCritMacro

  final def logValues(level: Level)(values: Any*): Unit = macro Strict.scLogValues

  final def logMethod[A](level: Level)(function: => A): A = macro Strict.scLogMethod[A]
  final def logMethod[A](level: Level, printTypes: Boolean)(function: => A): A = macro Strict.scLogMethodPrintTypes[A]
  final def logMethod[A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => A): A = macro Strict.scLogMethodPrintTypesImplicits[A]
}
