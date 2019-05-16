package com.github.pshirshov.izumi.logstage.api.routing

import java.util.concurrent.atomic.AtomicReference

import com.github.pshirshov.izumi.fundamentals.platform.console.TrivialLogger
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.rendering.RenderingOptions
import com.github.pshirshov.izumi.logstage.sink.FallbackConsoleSink

class StaticLogRouter extends LogRouter {
  private val proxied = new AtomicReference[LogRouter]()

  private val trivialLogger = TrivialLogger.make[FallbackConsoleSink](LogRouter.fallbackPropertyName, forceLog = true)
  private val fallbackSink = new FallbackConsoleSink(new StringRenderingPolicy(RenderingOptions()), trivialLogger)

  def setup(router: LogRouter): Unit = {
    proxied.set(router)
  }

  def get: LogRouter = proxied.get()

  override def acceptable(id: Log.LoggerId, messageLevel: Log.Level): Boolean = {
    proxied.get() match {
      case p if p != null =>
        p.acceptable(id, messageLevel)

      case null =>
        messageLevel >= Log.Level.Warn
    }
  }

  override def log(entry: Log.Entry): Unit = {
    proxied.get() match {
      case p if p != null =>
        p.log(entry)

      case null if acceptable(entry.context.static.id, entry.context.dynamic.level) =>
        fallbackSink.flush(entry)

      case _ =>
    }
  }

  override def close(): Unit = {
    proxied.set(null)
  }
}

object StaticLogRouter {
  final val instance = new StaticLogRouter()
}
