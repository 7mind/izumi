package izumi.logstage.api.logger

import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.logstage.api.Log
import izumi.logstage.api.Log.{CustomContext, LoggerId}

trait AbstractLogger {

//  /**
//    * More efficient aliases for [[log]]
//    *
//    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
//    * creating a `messageThunk` for a [[log]] call.
//    *
//    * They also look better in Intellij
//    * */
//  final def trace(message: String): Unit = macro scTraceMacro
//  final def debug(message: String): Unit = macro scDebugMacro
//  final def info(message: String): Unit = macro scInfoMacro
//  final def warn(message: String): Unit = macro scWarnMacro
//  final def error(message: String): Unit = macro scErrorMacro
//  final def crit(message: String): Unit = macro scCritMacro

  /** Log Entry if `logLevel` is above the threshold configured for this logger. */
  @inline final def log(entry: Log.Entry): Unit = {
    if (acceptable(entry.context.static.id, entry.context.dynamic.level)) {
      unsafeLog(entry)
    }
  }

  /**
    * Construct Entry and log if `logLevel` is above the threshold configured for this logger.
    *
    * Does not allocate Entry if `logLevel` is below the requirement
    * */
  @inline final def log(logLevel: Log.Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): Unit = {
    if (acceptable(LoggerId(pos.get.applicationPointId), logLevel)) {
      unsafeLog(Log.Entry.create(logLevel, messageThunk)(pos))
    }
  }

  @inline final def acceptable(logLevel: Log.Level)(implicit pos: CodePositionMaterializer): Boolean = {
    acceptable(LoggerId(pos.get.applicationPointId), logLevel)
  }

  /** Log irrespective of the log level threshold */
  def unsafeLog(entry: Log.Entry): Unit

  /** Check if `loggerId` is not blacklisted and `logLevel` is above the configured threshold */
  def acceptable(loggerId: LoggerId, logLevel: Log.Level): Boolean

  def withCustomContext(context: CustomContext): AbstractLogger
}
