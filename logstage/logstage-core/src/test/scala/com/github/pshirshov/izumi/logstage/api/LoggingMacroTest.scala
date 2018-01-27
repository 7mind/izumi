package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.model.Log.StaticContext
import com.github.pshirshov.izumi.logstage.model.{Log, LogReceiver}
import org.scalatest.WordSpec

import scala.util.Random

class AService(logger: MacroLogger) {
  def start(): Unit = {
    val context = logger("userId" -> "xxx")

    val arg = "this is an argument"
    context.debug(s"This would be automatically extended")

    logger.log.debug(s"Service started. argument: $arg, Random value: ${Random.self.nextInt()}")

    logger.log.debug("Just a string")
    logger.log.debug("Just " + 1)
  }
}

class LoggingMacroTest extends WordSpec {

  "Log macro" should {
    "work" in {
      val logger = setup()

      new AService(logger).start()
    }
  }

  private def setup() = {
    val recv = new LogReceiver {
      override def log(context: Log.Context, message: Log.Message): Unit = {
        System.err.println(s"ctx=$context, msg=$message")
        //new LogSink {}.flush(Log.Entry(message, context))
      }

      override def level: Log.Level = Log.Level.Debug
    }

    val ctx = StaticContext("test")
    val logger = MacroLogger(recv, ctx)
    logger
  }
}
