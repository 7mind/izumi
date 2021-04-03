package izumi.logstage.api.logger

import scala.language.experimental.macros

import izumi.logstage.macros.LoggerMacroMethods._

trait AbstractMacroRawLogger { this: AbstractLogger =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    */
  final def trace(message: String): Unit = macro scTraceMacroRaw
  final def debug(message: String): Unit = macro scDebugMacroRaw
  final def info(message: String): Unit = macro scInfoMacroRaw
  final def warn(message: String): Unit = macro scWarnMacroRaw
  final def error(message: String): Unit = macro scErrorMacroRaw
  final def crit(message: String): Unit = macro scCritMacroRaw
}
