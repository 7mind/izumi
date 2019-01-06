package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.api.Log.LoggerId
import com.github.pshirshov.izumi.logstage.macros.LoggerMacroMethods

import scala.language.experimental.macros

trait AbstractLogger {

  def log(entry: Log.Entry): Unit
  def log(logLevel: Log.Level)(messageThunk: => Log.Message)(implicit pos: CodePositionMaterializer): Unit

//  def acceptable(loggerId: LoggerId, logLevel: Log.Level): Boolean

  import LoggerMacroMethods._

  /**
    * More efficient aliases for [[log]]
    *
    * These directly splice an [[acceptable]] check before calling [[unsafeLog]] which is more efficient than
    * creating a thunk for a [[log]] call.
    * */
  final def trace(message: String): Unit = macro scTraceMacro
  final def debug(message: String): Unit = macro scDebugMacro
  final def info(message: String): Unit = macro scInfoMacro
  final def warn(message: String): Unit = macro scWarnMacro
  final def error(message: String): Unit = macro scErrorMacro
  final def crit(message: String): Unit = macro scCritMacro
}
