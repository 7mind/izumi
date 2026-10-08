package izumi.logstage.api

import izumi.logstage.api.Log.LogArg
import izumi.logstage.api.rendering.logunits.LogFormat
import izumi.logstage.api.rendering.{LogstageCodec, LogstageWriter, RenderingOptions, StringRenderingPolicy}
import izumi.logstage.api.strict.IzStrictLogger
import izumi.logstage.api.zioUtil.runZIO
import logstage.LogIO2
import logstage.strict.LogIO2Strict
import izumi.fundamentals.assertions.AssertionFailure
import izumi.distage.testkit.runner.spec.AnyWordSpec

class LoggerLogValuesTest extends AnyWordSpec {
  "Logger.logValues" should {

    "log values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      val value1 = 1

      logger.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")

      assertValues(testSink, integerArguments, "value_1=1, add=2, constant=1")
    }

    "log raw values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzLogger(sink = testSink)

      val value1 = 1

      logger.raw.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")

      assertValues(testSink, Nil, "1, (2,add), (1,constant)")
    }

    "log strict values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = IzStrictLogger(sink = testSink)

      val value1 = WithCustomCodec(1)

      val strictCodecErr = intercept[AssertionFailure](
        assertCompiles(
          """logger.logValues(Log.Level.Info)(value1, WithCustomCodec(testMethod(1)) -> "add", WithCustomCodec(1) -> "constant")"""
        )
      )
      assert(strictCodecErr.getMessage().contains("Implicit search failed"))

      val customIntCodec: LogstageCodec[WithCustomCodec] = {
        implicit val customIntCodec: LogstageCodec[WithCustomCodec] = newCustomCodec()

        logger.logValues(Log.Level.Info)(value1, WithCustomCodec(testMethod(1)) -> "add", WithCustomCodec(1) -> "constant")

        customIntCodec
      }

      assertValues(testSink, customArguments(customIntCodec), "value_1=a, add=aa, constant=a")
    }

    "logIO log values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = LogIO2.fromLogger[zio.IO](IzLogger(sink = testSink))

      val value1 = 1

      runZIO {
        logger.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")
      }

      assertValues(testSink, integerArguments, "value_1=1, add=2, constant=1")
    }

    "logIO log raw values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = LogIO2.fromLogger[zio.IO](IzLogger(sink = testSink))

      val value1 = 1

      runZIO {
        logger.raw.logValues(Log.Level.Info)(value1, testMethod(1) -> "add", 1 -> "constant")
      }

      assertValues(testSink, Nil, "1, (2,add), (1,constant)")
    }

    "logIO log strict values" in {
      val testSink = new TestSink(Some(new StringRenderingPolicy(RenderingOptions.simple, None)))
      val logger = LogIO2Strict.fromLogger[zio.IO](IzLogger(sink = testSink))

      val value1 = WithCustomCodec(1)

      val strictCodecErr = intercept[AssertionFailure](
        assertCompiles(
          """logger.logValues(Log.Level.Info)(value1, WithCustomCodec(testMethod(1)) -> "add", WithCustomCodec(1) -> "constant")"""
        )
      )
      assert(strictCodecErr.getMessage().contains("Implicit search failed"))

      val customIntCodec: LogstageCodec[WithCustomCodec] = {
        implicit val customIntCodec: LogstageCodec[WithCustomCodec] = newCustomCodec()

        runZIO {
          logger
            .logValues(Log.Level.Info)(value1, WithCustomCodec(testMethod(1)) -> "add", WithCustomCodec(1) -> "constant")
            .as(customIntCodec)
        }
      }

      assertValues(testSink, customArguments(customIntCodec), "value_1=a, add=aa, constant=a")
    }

  }

  case class WithCustomCodec(val int: Int)

  private def assertValues(sink: TestSink, expectedArgs: Seq[LogArg], expectedMessage: String): Unit = {
    val Seq(logEntry) = sink.fetch()
    assert(logEntry.message.args == expectedArgs)
    assert(LogFormat.Default.formatMessage(logEntry, RenderingOptions.simple).message == expectedMessage)
  }

  private def integerArguments: Seq[LogArg] = Seq(
    LogArg(Seq("value1"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
    LogArg(Seq("add"), 2, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
    LogArg(Seq("constant"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
  )

  private def customArguments(customIntCodec: LogstageCodec[WithCustomCodec]): Seq[LogArg] = Seq(
    LogArg(Seq("value1"), WithCustomCodec(1), hiddenName = false, Some(customIntCodec)),
    LogArg(Seq("add"), WithCustomCodec(2), hiddenName = false, Some(customIntCodec)),
    LogArg(Seq("constant"), WithCustomCodec(1), hiddenName = false, Some(customIntCodec)),
  )

  private def newCustomCodec(): LogstageCodec[WithCustomCodec] = new LogstageCodec[WithCustomCodec] {
    override def write(writer: LogstageWriter, value: WithCustomCodec): Unit = {
      writer.write(List.fill(value.int)("a").mkString)
    }
  }

  private def testMethod(x: Int): Int = x + x
}
