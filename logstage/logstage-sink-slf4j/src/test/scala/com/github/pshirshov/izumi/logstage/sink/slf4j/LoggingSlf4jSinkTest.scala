package com.github.pshirshov.izumi.logstage.sink.slf4j

import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.sink.{ConsoleSink, ExampleService}
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
  val sinkLegacySlf4jImpl = new LogSinkLegacySlf4jImpl(ConsoleSink.simplePolicy())


  def setupSlf4jLogger(): IzLogger = {
    IzLogger.basic(IzLogger.Level.Trace, sinkLegacySlf4jImpl)
  }
}
