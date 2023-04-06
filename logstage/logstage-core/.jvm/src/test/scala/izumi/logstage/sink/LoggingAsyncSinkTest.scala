package izumi.logstage.sink

import izumi.logstage.api.{IzLogger, TestSink}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt

class LoggingAsyncSinkTest extends AnyWordSpec {
  "Log macro" should {
    "support async sinks" in {
      val testSink = new TestSink()
      val asyncConsoleSinkJson = new ThreadingLogQueue(1.millis, 10)
      try {
        new ExampleService(IzLogger(IzLogger.Level.Trace, testSink, buffer = asyncConsoleSinkJson)).triggerManyMessages()
        assert(testSink.fetch().isEmpty)
        asyncConsoleSinkJson.start()
      } finally {
        asyncConsoleSinkJson.close()
      }

      assert(testSink.fetch().size == 100)
    }
  }
}
