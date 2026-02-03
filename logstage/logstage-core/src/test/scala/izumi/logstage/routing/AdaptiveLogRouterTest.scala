package izumi.logstage.routing

import izumi.logstage.api.{IzLogger, Log, TestSink}
import izumi.logstage.api.routing.ConfigurableLogRouter
import logstage.LogQueue
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

      logger.logTo("file")(Log.Level.Info)("file")
      assert(fileSink.fetch().size == 1)
      assert(consoleSink.fetch().isEmpty)

      logger.logTo("console")(Log.Level.Info)("console")
      assert(fileSink.fetch().size == 1)
      assert(consoleSink.fetch().size == 1)

      logger.log(Log.Level.Info)("default(both)")
      assert(fileSink.fetch().size == 2)
      assert(consoleSink.fetch().size == 2)
    }
  }
}
