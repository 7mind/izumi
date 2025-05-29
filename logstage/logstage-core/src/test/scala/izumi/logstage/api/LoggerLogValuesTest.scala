package izumi.logstage.api

import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.logunits.LogFormat
import izumi.logstage.api.rendering.{LogstageCodec, LogstageWriter, RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.strict.IzStrictLogger
import org.scalatest.wordspec.AnyWordSpec

class LoggerLogValuesTest extends AnyWordSpec {
  "Logger.logValues" should {

    "log values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      val value1 = 1

      logger.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")

      val Seq(logEntry) = testSink.fetch()

      val expectedArgs = Seq(
        LogArg(Seq("value1"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("add"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("constant"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      )
      assert(logEntry.message.args == expectedArgs)

      assert(LogFormat.Default.formatMessage(logEntry, RenderingOptions.simple).message == "value_1=1, add=2, constant=1")
    }

    "log raw values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      val value1 = 1

      logger.raw.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")

      val Seq(logEntry) = testSink.fetch()

      assert(logEntry.message.args == Nil)

      assert(LogFormat.Default.formatMessage(logEntry, RenderingOptions.simple).message == "1, (2,add), (1,constant)")
    }

    "log strict values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzStrictLogger(sink = testSink)

      implicit val customIntCodec: LogstageCodec[Int] = new LogstageCodec[Int] {
        override def write(writer: LogstageWriter, value: Int): Unit = {
          writer.write(List.fill(value)("a").mkString)
        }
      }

      val value1 = 1

      logger.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")

      val Seq(logEntry) = testSink.fetch()

      val expectedArgs = Seq(
        LogArg(Seq("value1"), 1, hiddenName = false, Some(customIntCodec)),
        LogArg(Seq("add"), 2, hiddenName = false, Some(customIntCodec)),
        LogArg(Seq("constant"), 1, hiddenName = false, Some(customIntCodec)),
      )
      assert(logEntry.message.args == expectedArgs)

      assert(LogFormat.Default.formatMessage(logEntry, RenderingOptions.simple).message == "value_1=a, add=aa, constant=a")
    }

  }

  private def testMethod(x: Int): Int = x + x
}
