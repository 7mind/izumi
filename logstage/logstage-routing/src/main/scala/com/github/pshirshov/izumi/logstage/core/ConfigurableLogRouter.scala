package com.github.pshirshov.izumi.logstage.core

import com.github.pshirshov.izumi.logstage.api.config.LogConfigService
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.{FallbackLogOutput, LogRouter}

class ConfigurableLogRouter
(
  logConfigService: LogConfigService
) extends LogRouter {
  override protected def doLog(entry: Log.Entry): Unit = {
    logConfigService
      .config(entry)
      .sinks
      .foreach {
        sink =>
          try  {
            sink.flush(entry)
          } catch {
            case e: Throwable =>
              FallbackLogOutput.flush(s"Log sink $sink failed", e)
          }
      }
  }


  override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = {
    logConfigService.threshold(id) <= messageLevel
  }
}


