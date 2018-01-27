package com.github.pshirshov.izumi.logstage.api.config

import com.github.pshirshov.izumi.logstage.api.logger.{LogFilter, LogSink}

case class LogMapping(filter: LogFilter, sink: LogSink)
