package izumi.logstage.api.logger

import izumi.logstage.api.Log.Level
import izumi.logstage.api.logger.AbstractMacroLogger.LogMethod
import izumi.logstage.macros.LoggerMacroMethods.*

import scala.language.experimental.macros

trait AbstractMacroLogger { this: AbstractLogger { type EncMode <: Singleton } =>

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
  final def audit(message: String): Unit = macro scAuditMacro

  final def traceTo(sinkKey: String)(message: String): Unit = macro scTraceToMacro
  final def debugTo(sinkKey: String)(message: String): Unit = macro scDebugToMacro
  final def infoTo(sinkKey: String)(message: String): Unit = macro scInfoToMacro
  final def warnTo(sinkKey: String)(message: String): Unit = macro scWarnToMacro
  final def errorTo(sinkKey: String)(message: String): Unit = macro scErrorToMacro
  final def critTo(sinkKey: String)(message: String): Unit = macro scCritToMacro
  final def auditTo(sinkKey: String)(message: String): Unit = macro scAuditToMacro

  final def logValues(level: Level)(values: Any*): Unit = macro scLogValues

  final def logMethod(level: Level, printTypes: Boolean = false, printImplicits: Boolean = false): LogMethod[EncMode] =
    new LogMethod[EncMode](this, level, printTypes, printImplicits)
}

object AbstractMacroLogger {

  final class LogMethod[Enc](val __getSelf: AbstractLogger, val __getSelfLevel: Level, val __printTypes: Boolean, val __printImplicits: Boolean) {
    def apply[A](function: => A): A = macro scLogMethod[A, Enc]
  }

}
