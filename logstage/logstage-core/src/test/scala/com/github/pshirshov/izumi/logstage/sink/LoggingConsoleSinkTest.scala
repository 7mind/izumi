package com.github.pshirshov.izumi.logstage.sink

import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.scalatest.WordSpec

class LoggingConsoleSinkTest extends WordSpec {
  import LoggingConsoleSinkTest._

  "Log macro" should {
    "support console backend" in {
      new ExampleService(setupConsoleLogger()).start()
    }
  }
}

object LoggingConsoleSinkTest {

  def setupConsoleLogger(): IzLogger = {
    IzLogger(IzLogger.Level.Trace, new ConsoleSink(ConsoleSink.coloringPolicy()))
  }

}

