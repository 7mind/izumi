package izumi.logstage.sink

import izumi.logstage.api.{IzLogger, TestSink}
import org.scalatest.wordspec.AnyWordSpec

class LoggingAsyncSinkTest extends AnyWordSpec {
  "Log macro" should {
    "support async sinks" in {
      val testSink = new TestSink()
      val asyncConsoleSinkJson = new QueueingSink(testSink)
      try {
        asyncConsoleSinkJson.start()
        new ExampleService(IzLogger(IzLogger.Level.Trace, asyncConsoleSinkJson)).triggerManyMessages()
        assert(testSink.fetch().isEmpty)
      } finally {
        asyncConsoleSinkJson.close()
      }

      assert(testSink.fetch().size == 100)
    }
  }
}
