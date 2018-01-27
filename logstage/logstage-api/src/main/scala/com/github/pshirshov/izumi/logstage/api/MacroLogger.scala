package com.github.pshirshov.izumi.logstage.api

//import com.github.pshirshov.izumi.logstage.api.ArgumentNameExtractionMacro.LogSC
import com.github.pshirshov.izumi.logstage.model.Log.CustomContext
import com.github.pshirshov.izumi.logstage.model.{Log, LogReceiver}

import scala.language.implicitConversions

class MacroLogger
(
  val log: MacroLoggerImpl
) {
  //implicit val passthrough: StringContext => LogSC = ArgumentNameExtractionMacro.LogSC

  implicit def withCustomContext(customContext: CustomContext): MacroLoggerImpl = {
    new MacroLoggerImpl(log.receiver, log.contextStatic, customContext)
  }

  implicit def withMapAsCustomContext(map: Map[String, Any]): MacroLoggerImpl = {
    new MacroLoggerImpl(log.receiver, log.contextStatic, CustomContext(map))
  }

  def apply[V](conv: Map[String, V]): MacroLoggerImpl = conv
  def apply[V](elems: (String, V)*): MacroLoggerImpl = elems.toMap

}

object MacroLogger {
  def apply(receiver: LogReceiver, contextStatic: Log.StaticContext): MacroLogger = {
    new MacroLogger(new MacroLoggerImpl(receiver, contextStatic, CustomContext.empty))
  }
}