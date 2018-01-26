package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.model.Log.StaticContext
import com.github.pshirshov.izumi.logstage.model.{Log, LogReceiver}
import org.scalatest.WordSpec

import scala.util.Random

class AService(logger: MacroLogger) {
  import logger._

  def start(): Unit = {
    val context = Map("userId" -> "xxx")

    val arg = "this is an argument"
    context.debug(m"This would be automatically extended")

    log.debug(m"Service started. argument: $arg, Random value: ${Random.self.nextInt()}")
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
