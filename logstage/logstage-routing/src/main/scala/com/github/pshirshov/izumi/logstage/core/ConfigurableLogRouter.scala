package com.github.pshirshov.izumi.logstage.core

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.logstage.api.config.LogConfigService
import com.github.pshirshov.izumi.logstage.api.logger.{RenderingOptions, RenderingPolicy}
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.logger.{LogRouter, LogSink}

class ConfigurableLogRouter
(
  logConfigService: LogConfigService
) extends LogRouter {
  override def log(entry: Log.Entry): Unit = {
    logConfigService
      .config(entry)
      .sinks
      .foreach(sink => sink.flush(entry))
  }


  override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = {
    logConfigService.threshold(id) <= messageLevel
  }
}


