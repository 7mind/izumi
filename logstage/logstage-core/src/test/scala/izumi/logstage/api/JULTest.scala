package izumi.logstage.api

import izumi.logstage.adapter.jul.LogstageJulLogger
import org.scalatest.wordspec.AnyWordSpec

import java.util.logging.{Level, Logger}

class JULTest extends AnyWordSpec {

  "Logstage JUL bridge" should {
    "support " in {
      val logger = IzLogger.apply()
      val bridge = new LogstageJulLogger(logger.router)
      bridge.installOnly()

      val julogger = Logger.getLogger(getClass.getName)
      val arr: Array[AnyRef] = Array("param1", "param2", new RuntimeException("param2"))
      julogger.log(Level.INFO, "message: {0} {1} {2}", arr)
    }
  }

}
