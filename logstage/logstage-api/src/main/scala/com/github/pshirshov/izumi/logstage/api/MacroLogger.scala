package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.model.Log.CustomContext
import com.github.pshirshov.izumi.logstage.model.{AbstractLogger, Log, LogReceiver}

import scala.language.implicitConversions


class MacroLogger
(
  override val receiver: LogReceiver
  , override val contextCustom: Log.CustomContext
) extends LoggingMacro
  with AbstractLogger {

  implicit def withCustomContext(newCustomContext: CustomContext): MacroLogger = {
    new MacroLogger(receiver, contextCustom + newCustomContext)
  }

  implicit def withMapAsCustomContext(map: Map[String, Any]): MacroLogger = {
    withCustomContext(CustomContext(map.toList))
  }

  def apply[V](conv: Map[String, V]): MacroLogger = conv

  def apply[V](elems: (String, V)*): MacroLogger = elems.toMap

}

object MacroLogger {
  def apply(receiver: LogReceiver): MacroLogger = {
    new MacroLogger(receiver, CustomContext.empty)
  }
}