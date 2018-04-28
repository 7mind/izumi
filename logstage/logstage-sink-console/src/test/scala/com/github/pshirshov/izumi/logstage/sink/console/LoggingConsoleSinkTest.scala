package com.github.pshirshov.izumi.logstage.sink.console

import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.routing.ExampleService
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
  import com.github.pshirshov.izumi.logstage.api.routing.LoggingMacroTest._

  val consoleSinkText = new ConsoleSink(coloringPolicy)

  def setupConsoleLogger(): IzLogger = {
    configureLogger(Seq(consoleSinkText))
  }

}

