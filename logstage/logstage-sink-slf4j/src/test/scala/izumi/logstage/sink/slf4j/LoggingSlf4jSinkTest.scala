package izumi.logstage.sink.slf4j

import izumi.logstage.api.IzLogger
import izumi.logstage.sink.{ConsoleSink, ExampleService}
import org.scalatest.wordspec.AnyWordSpec

class LoggingSlf4jSinkTest extends AnyWordSpec {

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
    IzLogger(IzLogger.Level.Trace, sinkLegacySlf4jImpl)
  }
}
