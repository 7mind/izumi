package izumi.logstage.sink

import izumi.logstage.api.IzLogger
import izumi.logstage.sink.ConsoleSink.ColoredConsoleSink
import org.scalatest.wordspec.AnyWordSpec

class LoggingConsoleSinkTest extends AnyWordSpec {
  import LoggingConsoleSinkTest._

  "Log macro" should {
    "support console backend" in {
      new ExampleService(setupConsoleLogger()).start()
    }
  }
}

object LoggingConsoleSinkTest {

  def setupConsoleLogger(): IzLogger = {
    IzLogger(IzLogger.Level.Trace, ColoredConsoleSink)
  }

}
