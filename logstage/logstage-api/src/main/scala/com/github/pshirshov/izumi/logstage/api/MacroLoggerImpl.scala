package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.model.{AbstractLogger, Log, LogReceiver}

class MacroLoggerImpl
(
  override val receiver: LogReceiver
  , override val contextStatic: Log.StaticContext
  , override val contextCustom: Log.CustomContext
) extends LoggingMacro
  with AbstractLogger {}
