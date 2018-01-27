package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.logstage.model.{Log, LogReceiver}
import org.scalatest.WordSpec

import scala.util.Random

class AService(logger: MacroLogger) {
  def start(): Unit = {
    val context = logger("userId" -> "xxx")
    val subcontext = context("c" -> "d")

    val arg = "this is an argument"

    context.trace(s"This would be automatically extended") 
    logger.debug(s"Service started. argument: $arg, Random value: ${Random.self.nextInt()}")
    subcontext.info("Just a string")
    logger.warn("Just an integer: " + 1)
    logger.crit(s"This is an issue: ${2+2 == 4}")
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

      override def level: Log.Level = Log.Level.Trace
    }

    val logger = MacroLogger(recv) 
    logger
  }
}
