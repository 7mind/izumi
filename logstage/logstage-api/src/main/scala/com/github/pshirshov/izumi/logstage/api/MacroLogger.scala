package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.model.Log.CustomContext
import com.github.pshirshov.izumi.logstage.model.{AbstractLogger, Log, LogReceiver}

import scala.language.implicitConversions


class MacroLogger
(
  override val receiver: LogReceiver
  , override val contextStatic: Log.StaticContext
  , override val contextCustom: Log.CustomContext
) extends LoggingMacro
  with AbstractLogger {
  implicit def withCustomContext(customContext: CustomContext): MacroLogger = {
    new MacroLogger(receiver, contextStatic, customContext)
  }

  implicit def withMapAsCustomContext(map: Map[String, Any]): MacroLogger = {
    new MacroLogger(receiver, contextStatic, CustomContext(map))
  }

  def apply[V](conv: Map[String, V]): MacroLogger = conv

  def apply[V](elems: (String, V)*): MacroLogger = elems.toMap

}

object MacroLogger {
  def apply(receiver: LogReceiver, contextStatic: Log.StaticContext): MacroLogger = {
    new MacroLogger(receiver, contextStatic, CustomContext.empty)
  }
}