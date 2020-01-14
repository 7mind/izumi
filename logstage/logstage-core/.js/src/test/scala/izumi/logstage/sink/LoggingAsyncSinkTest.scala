package izumi.logstage.sink

import izumi.logstage.api.{IzLogger, TestSink}
import org.scalatest.wordspec.AnyWordSpec

class LoggingAsyncSinkTest extends AnyWordSpec {
  "Log macro" should {
    "support async sink api in sync mode" in {
      val testSink = new TestSink()
      val asyncConsoleSinkJson = new QueueingSink(testSink)
      try {
        new ExampleService(IzLogger(IzLogger.Level.Trace, asyncConsoleSinkJson)).triggerManyMessages()
        assert(testSink.fetch().size == 100)
        asyncConsoleSinkJson.start()
      } finally {
        asyncConsoleSinkJson.close()
      }

      assert(testSink.fetch().size == 100)
    }
  }
}
