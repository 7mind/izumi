package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.logstage.api.{IzLogger, TestSink}
import org.scalatest.WordSpec

class LoggingAsyncSinkTest extends WordSpec {
  "Log macro" should {
    "support async sinks" in {
      val testSink = new TestSink()
      val asyncConsoleSinkJson = new QueueingSink(testSink)
      try {
        new ExampleService(IzLogger(IzLogger.Level.Trace, asyncConsoleSinkJson)).work()
        assert(testSink.fetch().isEmpty)
        asyncConsoleSinkJson.start()
      } finally {
        asyncConsoleSinkJson.close()
      }

      assert(testSink.fetch().size == 100)
    }
  }
}
