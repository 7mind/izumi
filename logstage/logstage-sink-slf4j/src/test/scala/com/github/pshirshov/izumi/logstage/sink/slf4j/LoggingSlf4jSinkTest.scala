package com.github.pshirshov.izumi.logstage.sink.slf4j

import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.routing.ExampleService
import org.scalatest.WordSpec

class LoggingSlf4jSinkTest extends WordSpec {

  import LoggingSlf4jSinkTest._

  "Log macro" should {
    "support slf4j legacy backend" in {
      new ExampleService(setupSlf4jLogger()).start()
    }
  }
}

object LoggingSlf4jSinkTest {
  import com.github.pshirshov.izumi.logstage.api.routing.LoggingMacroTest._
  val sinkLegacySlf4jImpl = new LogSinkLegacySlf4jImpl(simplePolicy)


  def setupSlf4jLogger(): IzLogger = {
    configureLogger(Seq(sinkLegacySlf4jImpl))
  }
}
