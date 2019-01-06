package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.logstage.macros.LoggerMacroMethods

import scala.language.experimental.macros

trait AbstractLogger {
  def log(entry: Log.Entry): Unit
  def log(logLevel: Log.Level)(message: Log.Message)(implicit pos: CodePositionMaterializer): Unit

  import LoggerMacroMethods._

  /** Aliases for [[AbstractLogger.log]] that look better in Intellij */
  final def trace(message: String): Unit = macro scTraceMacro
  final def debug(message: String): Unit = macro scDebugMacro
  final def info(message: String): Unit = macro scInfoMacro
  final def warn(message: String): Unit = macro scWarnMacro
  final def error(message: String): Unit = macro scErrorMacro
  final def crit(message: String): Unit = macro scCritMacro
}
