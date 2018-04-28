package com.github.pshirshov.izumi.logstage.api.routing

import java.nio.file.Files

import com.github.pshirshov.izumi.{FileSink, FileSinkConfig, Rotation}
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log, TestSink}
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import org.scalatest.WordSpec

import scala.util.{Random, Try}

@ExposedTestScope
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

class LoggingMacroTest extends WordSpec {

  import LoggingMacroTest._

  "Log macro" should {
    "support async sinks" in {
      val testSink = new TestSink()
      val asyncConsoleSinkJson = new QueueingSink(testSink)
      try {
        new ExampleService(configureLogger(Seq(asyncConsoleSinkJson))).work()
        assert(testSink.fetch().isEmpty)
        asyncConsoleSinkJson.start()
      } finally {
        asyncConsoleSinkJson.close()
      }

      assert(testSink.fetch().size == 100)
    }

    "support file sink" in {
      new ExampleService(setupFileLogger()).start()
    }
  }
}

@ExposedTestScope
object LoggingMacroTest {

  val coloringPolicy = new StringRenderingPolicy(RenderingOptions())
  val simplePolicy = new StringRenderingPolicy(RenderingOptions(withExceptions = false, withColors = false))
  val jsonPolicy = new JsonRenderingPolicy()
  val consoleSinkText = new ConsoleSink(coloringPolicy)
  val consoleSinkJson = new ConsoleSink(jsonPolicy)
  val fileSinkText = new FileSink(simplePolicy, consoleSinkText, FileSinkConfig(3, "logstage", Rotation.EnabledRotation(2)))

  def setupConsoleLogger(): IzLogger = {
    configureLogger(Seq(consoleSinkText))
  }

  def setupFileLogger(): IzLogger = {
    configureLogger(Seq(fileSinkText))
  }

  def setupJsonLogger(): IzLogger = {
    configureLogger(Seq(consoleSinkJson))
  }

  def configureLogger(sinks: Seq[LogSink]): IzLogger = {
    val router: ConfigurableLogRouter = mkRouter(sinks :_*)
    new IzLogger(router, CustomContext.empty)
  }

  def mkRouter(sinks: LogSink*): ConfigurableLogRouter = {
    val configService = new LogConfigServiceStaticImpl(Map.empty, LoggerConfig(Log.Level.Trace, sinks))
    val router = new ConfigurableLogRouter(configService)
    router
  }
}
