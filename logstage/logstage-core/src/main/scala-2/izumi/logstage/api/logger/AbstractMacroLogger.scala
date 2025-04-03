package izumi.logstage.api.logger

import izumi.logstage.api.Log.Level
import izumi.logstage.macros.LoggerMacroMethods.*

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
  final def trace(message: String): Unit = macro scTraceMacro
  final def debug(message: String): Unit = macro scDebugMacro
  final def info(message: String): Unit = macro scInfoMacro
  final def warn(message: String): Unit = macro scWarnMacro
  final def error(message: String): Unit = macro scErrorMacro
  final def crit(message: String): Unit = macro scCritMacro

  final def logMethod[A](level: Level)(function: => A): A = macro scLogMethod[A]
  final def logMethod[A](level: Level, printTypes: Boolean)(function: => A): A = macro scLogMethodPrintTypes[A]
  final def logMethod[A](level: Level, printTypes: Boolean, printImplicits: Boolean)(function: => A): A = macro scLogMethodPrintTypesImplicits[A]
}
