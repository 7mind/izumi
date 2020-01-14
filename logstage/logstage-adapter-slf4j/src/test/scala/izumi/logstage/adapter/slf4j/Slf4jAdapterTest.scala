package izumi.logstage.adapter.slf4j

import izumi.logstage.api.routing.{ConfigurableLogRouter, StaticLogRouter}
import izumi.logstage.api.{IzLogger, TestSink}
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory
import org.slf4j.impl.StaticLoggerBinder

class Slf4jAdapterTest extends AnyWordSpec {

  private final val logger = LoggerFactory.getLogger(getClass)

  "slf4j logger adaper" should {
    "pass logs to LogStage" in {
      val sink = new TestSink()
      val router = ConfigurableLogRouter(IzLogger.Level.Trace, sink)

      StaticLogRouter.instance.setup(router)

      assert(StaticLoggerBinder.getSingleton != null)
      assert(StaticLoggerBinder.getSingleton.getLoggerFactory != null)
      assert(StaticLoggerBinder.getSingleton.getLoggerFactory.isInstanceOf[LogstageLoggerFactory])
      assert(StaticLogRouter.instance.get() eq router)

      logger.trace("Debug message")
      logger.trace("Debug message: {}")
      logger.debug("Debug message: {}", 1)
      logger.info("Debug message: {}, {}", 1, 2)
      logger.warn("Debug message: {}", Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1))
      logger.error("Debug message: {}", new RuntimeException())

      assert(sink.fetch().size == 6)
    }
  }

}


