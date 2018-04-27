package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.{FileSink, FileSinkConfig, Rotation}
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.logstage.TestSink
import com.github.pshirshov.izumi.logstage.api.logger.RenderingOptions
import com.github.pshirshov.izumi.logstage.api.rendering.StringRenderingPolicy
import com.github.pshirshov.izumi.logstage.core.{ConfigurableLogRouter, LogConfigServiceStaticImpl}
import com.github.pshirshov.izumi.logstage.model.Log
import com.github.pshirshov.izumi.logstage.model.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.model.logger.{LogSink, QueueingSink}
import com.github.pshirshov.izumi.logstage.sink.console.ConsoleSink
import org.scalatest.WordSpec

import scala.util.Random

class ExampleService(logger: IzLogger) {
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

  def work(): Unit = {
    (1 to 100).foreach {
      i =>
        logger.debug(s"step $i")
    }
  }
}


@ExposedTestScope
class LoggingMacroTest extends WordSpec {

  import LoggingMacroTest._

  "Log macro" should {
    "support console sink" in {
      new ExampleService(setupConsoleLogger()).start()
    }

    "support console sink with json output policy" in {
      new ExampleService(setupJsonLogger()).start()
    }

    "support async sinks" in {
      val testSink = new TestSink()
      val asyncConsoleSinkJson = new QueueingSink(testSink)
      try {
        new ExampleService(configureLogger(Seq(asyncConsoleSinkJson))).work()
        assert(testSink.fetch.isEmpty)
        asyncConsoleSinkJson.start()
      } finally {
        asyncConsoleSinkJson.close()
      }

      assert(testSink.fetch.size == 100)
    }
  }
}

object LoggingMacroTest {

  val coloringPolicy = new StringRenderingPolicy(RenderingOptions())
  val simplePolicy = new StringRenderingPolicy(RenderingOptions(withExceptions = false, withColors = false))
  val jsonPolicy = new JsonRenderingPolicy()
  val consoleSinkText = new ConsoleSink(coloringPolicy)
  val consoleSinkJson = new ConsoleSink(jsonPolicy)
  val finelSinkJson = new FileSink(jsonPolicy, consoleSinkJson, FileSinkConfig(10, "/Users/rtwnk/Desktop", Rotation.DisabledRotation))

  def setupConsoleLogger(): IzLogger = {
    configureLogger(Seq(finelSinkJson))
//    configureLogger(Seq(consoleSinkText, finelSinkJson))
  }

  def setupJsonLogger(): IzLogger = {
    configureLogger(Seq(consoleSinkJson))
  }

  def configureLogger(sinks: Seq[LogSink]): IzLogger = {
    val router: ConfigurableLogRouter = mkRouter(sinks)
    IzLogger(router)
  }

  def mkRouter(sinks: Seq[LogSink]): ConfigurableLogRouter = {
    val configService = new LogConfigServiceStaticImpl(Map.empty, LoggerConfig(Log.Level.Trace, sinks))
    val router = new ConfigurableLogRouter(configService)
    router
  }
}
