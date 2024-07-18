package izumi.logstage.sink

import izumi.logstage.api.IzLogger
import izumi.logstage.api.routing.ConfigurableLogRouter
import izumi.logstage.sink.ConsoleSink.ColoredConsoleSink
import logstage.{Log, LogQueue}
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
    IzLogger.apply(
      ConfigurableLogRouter.apply(
        Log.Level.Trace,
        Seq(ColoredConsoleSink),
        Map(
          "izumi.logstage.sink.ExampleService.start:26,27" -> Log.Level.Error
        ),
        LogQueue.Immediate,
      )
    )

    // IzLogger(IzLogger.Level.Trace, ColoredConsoleSink)
  }

}
