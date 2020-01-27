package izumi.logstage.api.logger

import izumi.logstage.macros.LoggerMacroMethods._

import scala.language.experimental.macros

trait AbstractMacroStrictLogger {
  this: AbstractLogger =>

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a `messageThunk` for a [[log]] call.
    *
    * They also look better in Intellij
    **/
  final def trace(message: String): Unit = macro scTraceMacroStrict
  final def debug(message: String): Unit = macro scDebugMacroStrict
  final def info(message: String): Unit = macro scInfoMacroStrict
  final def warn(message: String): Unit = macro scWarnMacroStrict
  final def error(message: String): Unit = macro scErrorMacroStrict
  final def crit(message: String): Unit = macro scCritMacroStrict

}
