package com.github.pshirshov.izumi.logstage.api.routing

import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.config.LoggerConfig
import com.github.pshirshov.izumi.logstage.api.logger.LogSink
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log, TestSink}
import org.scalatest.WordSpec

import scala.util.Random

@ExposedTestScope
class ExampleService(logger: IzLogger) {
  def start(): Unit = {
    val loggerWithContext = logger("userId" -> "xxx")
    val loggerWithSubcontext = loggerWithContext("custom" -> "value")

    val justAnArg = "this is an argument"
    val justAList = List[Any](10, "green", "bottles")
    loggerWithContext.trace(s"This would be automatically extended")
    logger.debug(s"Service started. argument: $justAnArg, another arg: $justAList, Random value: ${Random.self.nextInt() -> "random value"}")
    loggerWithSubcontext.info("Just a string")
    logger.crit(s"This is an expression: ${Random.nextInt() -> "xxx"}")
    logger.crit(s"This is an expression with invisible argument name: ${Random.nextInt() -> "xxx" -> null}")
    val exception = new RuntimeException("Oy vey!")
    exception.setStackTrace(exception.getStackTrace.slice(0, 3))
    logger.crit(s"A failure happened: $exception")

    // cornercases
    val arg1 = 5
    val nullarg = null
    logger.warn("[Cornercase] non-interpolated expression: " + 1)
    logger.crit(s"[Cornercase] Anonymous expression: ${2 + 2 == 4}, another one: ${5 * arg1 == 25}")
    logger.crit(s"[Cornercase] null value: $nullarg")

    val badObj = new Object {
      override def toString: String = throw exception
    }
    logger.crit(s"[Cornercase] exception: ${badObj -> "bad"}")
  }

  def work(): Unit = {
    (1 to 100).foreach {
      i =>
        logger.debug(s"step $i")
    }
  }
}

class LoggingAsyncSinkTest extends WordSpec {

  import LoggingAsyncSinkTest._

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
  }
}

@ExposedTestScope
object LoggingAsyncSinkTest {

  def coloringPolicy(renderingLayout : Option[String] = None) = new StringRenderingPolicy(RenderingOptions(), renderingLayout)
  def simplePolicy(renderingLayout : Option[String] = None) = new StringRenderingPolicy(RenderingOptions(withExceptions = false, withColors = false), renderingLayout)


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
