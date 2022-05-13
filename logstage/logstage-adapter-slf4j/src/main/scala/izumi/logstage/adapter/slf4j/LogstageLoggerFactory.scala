package izumi.logstage.adapter.slf4j

import java.util.concurrent.ConcurrentHashMap

import izumi.logstage.api.routing.StaticLogRouter
import org.slf4j.{ILoggerFactory, Logger}

class LogstageLoggerFactory extends ILoggerFactory {
  private val loggers = new ConcurrentHashMap[String, Logger]

  override def getLogger(name: String): Logger = {
    loggers.computeIfAbsent(name, n => new LogstageSlf4jLogger(n, StaticLogRouter.instance))
  }
}
