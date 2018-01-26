package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.ArgumentNameExtractionMacro.LogSC
import com.github.pshirshov.izumi.logstage.model.{Log, LogReceiver}

class MacroLogger
(
  val log: MacroLoggerImpl
) {
  implicit val passthrough: StringContext => LogSC = ArgumentNameExtractionMacro.LogSC
}

object MacroLogger {
  def apply(contextStatic: Log.StaticContext, receiver: LogReceiver): MacroLogger = {
    new MacroLogger(new MacroLoggerImpl(receiver, contextStatic))
  }
}