package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.api.logger.RenderingOptions
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy
import com.github.pshirshov.izumi.logstage.core.{ConfigurableLogRouter, LogConfigServiceStaticImpl}
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink
import com.github.pshirshov.izumi.logstage.sink.slf4j.LogSinkLegacySlf4jImpl
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
    val coloringPolicy = new StringRenderingPolicy(RenderingOptions())
    val simplePolicy = new StringRenderingPolicy(RenderingOptions(withExceptions = false, withColors = false))
    val jsonPolicy = new JsonRenderingPolicy()
    val sinks = Seq(new ConsoleSink(coloringPolicy), new ConsoleSink(jsonPolicy), new LogSinkLegacySlf4jImpl(simplePolicy))
    val configService = new LogConfigServiceStaticImpl(Map.empty, LoggerConfig(Log.Level.Trace, sinks))
    val router = new ConfigurableLogRouter(configService)
    IzLogger(router)
  }
  
}

object LoggingMacroTest {

  class AService(logger: IzLogger) {
    def start(): Unit = {
      val loggerWithContext = logger("userId" -> "xxx")
      val loggerWithSubcontext = loggerWithContext("custom" -> "value")

      val arg = "this is an argument"

      loggerWithContext.trace(s"This would be automatically extended")
      logger.debug(s"Service started. argument: $arg, Random value: ${Random.self.nextInt()}")
      loggerWithSubcontext.info("Just a string")
      logger.warn("Just an integer: " + 1)
      val arg1 = 5
      logger.crit(s"This is an expression: ${2 + 2 == 4} and this is an other one: ${5 * arg1 == 25}")
      val t = new RuntimeException("Oy vey!")
      logger.crit(s"A failure happened: $t")
    }
  }

}