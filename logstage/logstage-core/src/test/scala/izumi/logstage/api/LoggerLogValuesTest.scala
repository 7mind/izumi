package izumi.logstage.api

import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.logunits.LogFormat
import izumi.logstage.api.rendering.{LogstageCodec, LogstageWriter, RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.strict.IzStrictLogger
import izumi.logstage.api.zioUtil.runZIO
import logstage.LogIO2
import logstage.strict.LogIO2Strict
import org.scalatest.exceptions.TestFailedException
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

      val value1 = WithCustomCodec(1)

      val strictCodecErr = intercept[TestFailedException](
        assertCompiles(
          """logger.logValues(Log.Level.Info)(value1, WithCustomCodec(testMethod(1)) -> "add", WithCustomCodec(1) -> "constant")"""
        )
      )
      assert(strictCodecErr.getMessage().contains("Implicit search failed"))

      val customIntCodec: LogstageCodec[WithCustomCodec] = {
        implicit val customIntCodec: LogstageCodec[WithCustomCodec] = new LogstageCodec[WithCustomCodec] {
          override def write(writer: LogstageWriter, value: WithCustomCodec): Unit = {
            writer.write(List.fill(value.int)("a").mkString)
          }
        }

        logger.logValues(Log.Level.Info)(value1, WithCustomCodec(testMethod(1)) -> "add", WithCustomCodec(1) -> "constant")

        customIntCodec
      }

      val Seq(logEntry) = testSink.fetch()

      val expectedArgs = Seq(
        LogArg(Seq("value1"), WithCustomCodec(1), hiddenName = false, Some(customIntCodec)),
        LogArg(Seq("add"), WithCustomCodec(2), hiddenName = false, Some(customIntCodec)),
        LogArg(Seq("constant"), WithCustomCodec(1), hiddenName = false, Some(customIntCodec)),
      )
      assert(logEntry.message.args == expectedArgs)

      assert(LogFormat.Default.formatMessage(logEntry, RenderingOptions.simple).message == "value_1=a, add=aa, constant=a")
    }

    "logIO log values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = LogIO2.fromLogger[zio.IO](IzLogger(sink = testSink))

      val value1 = 1

      runZIO {
        logger.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")
      }

      val Seq(logEntry) = testSink.fetch()

      val expectedArgs = Seq(
        LogArg(Seq("value1"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("add"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        LogArg(Seq("constant"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
      )
      assert(logEntry.message.args == expectedArgs)

      assert(LogFormat.Default.formatMessage(logEntry, RenderingOptions.simple).message == "value_1=1, add=2, constant=1")
    }

    "logIO log raw values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = LogIO2.fromLogger[zio.IO](IzLogger(sink = testSink))

      val value1 = 1

      runZIO {
        logger.raw.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")
      }

      val Seq(logEntry) = testSink.fetch()

      assert(logEntry.message.args == Nil)

      assert(LogFormat.Default.formatMessage(logEntry, RenderingOptions.simple).message == "1, (2,add), (1,constant)")
    }

    "logIO log strict values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = LogIO2Strict.fromLogger[zio.IO](IzLogger(sink = testSink))

      val value1 = WithCustomCodec(1)

      val strictCodecErr = intercept[TestFailedException](
        assertCompiles(
          """logger.logValues(Log.Level.Info)(value1, WithCustomCodec(testMethod(1)) -> "add", WithCustomCodec(1) -> "constant")"""
        )
      )
      assert(strictCodecErr.getMessage().contains("Implicit search failed"))

      val customIntCodec: LogstageCodec[WithCustomCodec] = {
        implicit val customIntCodec: LogstageCodec[WithCustomCodec] = new LogstageCodec[WithCustomCodec] {
          override def write(writer: LogstageWriter, value: WithCustomCodec): Unit = {
            writer.write(List.fill(value.int)("a").mkString)
          }
        }

        runZIO {
          logger
            .logValues(Log.Level.Info)(value1, WithCustomCodec(testMethod(1)) -> "add", WithCustomCodec(1) -> "constant")
            .as(customIntCodec)
        }
      }

      val Seq(logEntry) = testSink.fetch()

      val expectedArgs = Seq(
        LogArg(Seq("value1"), WithCustomCodec(1), hiddenName = false, Some(customIntCodec)),
        LogArg(Seq("add"), WithCustomCodec(2), hiddenName = false, Some(customIntCodec)),
        LogArg(Seq("constant"), WithCustomCodec(1), hiddenName = false, Some(customIntCodec)),
      )
      assert(logEntry.message.args == expectedArgs)

      assert(LogFormat.Default.formatMessage(logEntry, RenderingOptions.simple).message == "value_1=a, add=aa, constant=a")
    }

  }

  case class WithCustomCodec(val int: Int)

  private def testMethod(x: Int): Int = x + x
}
