package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy
import com.github.pshirshov.izumi.logstage.core.{ConfigurableLogRouter, LogConfigServiceStaticImpl}
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink
import org.scalatest.WordSpec

import scala.util.Random


class LoggingMacroTest extends WordSpec {

  import LoggingMacroTest._

  "Log macro" should {
    "work" in {
      val logger = setupLogger()

      new AService(logger).start()
    }
  }

  private def setupLogger() = {
    val sinks = Seq(new ConsoleSink(new StringRenderingPolicy(true)))
    val configService = new LogConfigServiceStaticImpl(Map.empty, LoggerConfig(Log.Level.Trace, sinks))
    val router = new ConfigurableLogRouter(configService)
    IzLogger(router)
  }
  
}

object LoggingMacroTest {

  class AService(logger: IzLogger) {
    def start(): Unit = {
      val loggerWithContext = logger("userId" -> "xxx")
      val loggerWithSubcontext = loggerWithContext("c" -> "d")

      val arg = "this is an argument"

      loggerWithContext.trace(s"This would be automatically extended")
      logger.debug(s"Service started. argument: $arg, Random value: ${Random.self.nextInt()}")
      loggerWithSubcontext.info("Just a string")
      logger.warn("Just an integer: " + 1)
      logger.crit(s"This is an issue: ${2 + 2 == 4}")
    }
  }

}