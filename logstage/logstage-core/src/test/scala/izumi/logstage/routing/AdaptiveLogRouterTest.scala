package izumi.logstage.routing

import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.api.zioUtil.runZIO
import izumi.logstage.api.{IzLogger, Log, TestSink}
import logstage.{LogIO2, LogQueue}
import org.scalatest.wordspec.AnyWordSpec

class AdaptiveLogRouterTest extends AnyWordSpec {

  "Adaptive router" should {
    "route entries based on context sink key" in {
      val fileSink = new TestSink()
      val consoleSink = new TestSink()

      val router = ConfigurableLogRouter.makeAdaptive(
        Log.Level.Trace,
        Map(
          "file" -> Seq(fileSink),
          "console" -> Seq(consoleSink)
        ),
        Map.empty,
        LogQueue.Immediate,
      )

      val logger = IzLogger(router)

      logger.logTo("file")(Log.Level.Info)("test log to file")
      assert(fileSink.fetch().size == 1)
      assert(consoleSink.fetch().isEmpty)

      logger.infoTo("file")("test log to file #2")
      assert(fileSink.fetch().size == 2)
      assert(consoleSink.fetch().isEmpty)

      logger.warnTo("console")("test log to console")
      assert(fileSink.fetch().size == 2)
      assert(consoleSink.fetch().size == 1)

      logger.logTo("console")(Log.Level.Warn)("test log to console #2")
      assert(fileSink.fetch().size == 2)
      assert(consoleSink.fetch().size == 2)

      logger.log(Log.Level.Info)("default test log to both sinks")
      assert(fileSink.fetch().size == 3)
      assert(consoleSink.fetch().size == 3)
    }

    "route entries for LogIO" in {
      val fileSink = new TestSink()
      val consoleSink = new TestSink()

      val router = ConfigurableLogRouter.makeAdaptive(
        Log.Level.Trace,
        Map(
          "file" -> Seq(fileSink),
          "console" -> Seq(consoleSink)
        ),
        Map.empty,
        LogQueue.Immediate,
      )

      val logger = LogIO2.fromLogger[zio.IO](IzLogger(router))

      runZIO {
        for {
          _ <- logger.infoTo("file")("info to file")
          _ = assert(fileSink.fetch().size == 1)
          _ = assert(consoleSink.fetch().isEmpty)

          _ <- logger.warnTo("console")("warn to console")
          _ = assert(fileSink.fetch().size == 1)
          _ = assert(consoleSink.fetch().size == 1)

          _ <- logger.log(Log.Level.Info)("log to both sinks")
          _ = assert(fileSink.fetch().size == 2)
          _ = assert(consoleSink.fetch().size == 2)
        } yield ()
      }
    }

    "route sink filtered by threshold" in {
      val consoleSink = new TestSink()

      val router = ConfigurableLogRouter.makeAdaptive(
        Log.Level.Warn,
        Map(
          "console" -> Seq(consoleSink)
        ),
        Map.empty,
        LogQueue.Immediate,
      )

      val logger = IzLogger(router)
      logger.debugTo("console")("below threshold")

      assert(consoleSink.fetch().isEmpty)
    }
  }
}
