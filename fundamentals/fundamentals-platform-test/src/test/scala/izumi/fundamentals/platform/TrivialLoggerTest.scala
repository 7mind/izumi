package izumi.fundamentals.platform

import izumi.fundamentals.platform.console.{AbstractStringTrivialSink, TrivialLogger}
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable

class TrivialLoggerTest extends AnyWordSpec {

  "system properties work in TrivialLogger" in {

    System.setProperty("demotestizumi1", "true")
    System.setProperty("demotestizumi2.demo", "true")
    System.setProperty("demotestizumi3.demo.test", "true")

    val signals = mutable.Set.empty[String]

    val signalSink = new AbstractStringTrivialSink {
      override def flush(value: => String): Unit = signals += value
      override def flushError(value: => String): Unit = signals += value
    }

    val logger1 = TrivialLogger.make[TrivialLoggerTest]("demotestizumi1.demo.test", TrivialLogger.Config(signalSink))
    val logger2 = TrivialLogger.make[TrivialLoggerTest]("demotestizumi2.demo.test", TrivialLogger.Config(signalSink))
    val logger3 = TrivialLogger.make[TrivialLoggerTest]("demotestizumi3.demo.test", TrivialLogger.Config(signalSink))
    val logger4 = TrivialLogger.make[TrivialLoggerTest]("demotestizumi4.demo.test", TrivialLogger.Config(signalSink))

    logger1.log("1")
    logger2.log("2")
    logger3.log("3")
    logger4.log("4")

    assert(
      signals.toSet == Set(
        "TrivialLoggerTest: 1",
        "TrivialLoggerTest: 2",
        "TrivialLoggerTest: 3",
      )
    )
  }

}
