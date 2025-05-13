package izumi.logstage.api

import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.{LogstageCodec, RenderingOptions, StringRenderingPolicy}
import org.scalatest.wordspec.AnyWordSpec

class LoggerLogValuesTest extends AnyWordSpec {
  "Logger.logValues" should {
    "log values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      val value1 = 1

      logger.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")
      val logEntry = testSink.fetch().head

      val args = Seq(
        LogArg(Seq("value1"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("add"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("constant"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      )

      assert(logEntry.message.args == args)
    }
  }

  private def testMethod(x: Int): Int = x + x
}
