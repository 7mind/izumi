package com.github.pshirshov.izumi.logstage.adapter.slf4j

import com.github.pshirshov.izumi.logstage.api.routing.StaticLogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, TestSink}
import org.scalatest.WordSpec
import org.slf4j.LoggerFactory

class Slf4jAdapterTest extends WordSpec {

  private val logger = LoggerFactory.getLogger(getClass)

  "slf4j logger adaper" should {
    "pass logs to LogStage" in {
      val sink = new TestSink()

      StaticLogRouter.instance.setup(IzLogger.simpleRouter(IzLogger.Level.Trace, sink))

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


