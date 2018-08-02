package com.github.pshirshov.izumi.logstage.adapter.slf4j

import com.github.pshirshov.izumi.logstage.api.TestSink
import com.github.pshirshov.izumi.logstage.api.routing.StaticLogRouter
import com.github.pshirshov.izumi.logstage.api.routing.LoggingAsyncSinkTest
import org.scalatest.WordSpec
import org.slf4j.LoggerFactory

class Slf4jAdapterTest extends WordSpec {

  private val logger = LoggerFactory.getLogger(getClass)

  "slf4j logger adaper" should {
    "pass logs to LogStage" in {
      val sink = new TestSink()

      StaticLogRouter.instance.setup(LoggingAsyncSinkTest.mkRouter(
        sink
      ))

      logger.trace(s"Debug message")
      logger.trace(s"Debug message: {}")
      logger.debug(s"Debug message: {}", 1)
      logger.info(s"Debug message: {}, {}", 1, 2)
      logger.warn(s"Debug message: {}", Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1))
      logger.error(s"Debug message: {}", new RuntimeException())

      assert(sink.fetch().size == 6)
    }
  }

}


