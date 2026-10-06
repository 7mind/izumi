package izumi.logstage.sink.slf4j

import izumi.logstage.api.IzLogger
import izumi.logstage.api.rendering.RenderingPolicy
import izumi.logstage.sink.ExampleService
import izumi.distage.testkit.runner.spec.AnyWordSpec

class LoggingSlf4jSinkTest extends AnyWordSpec {

  import LoggingSlf4jSinkTest._

  "Log macro" should {
    "support slf4j legacy backend" in {
      new ExampleService(setupSlf4jLogger()).start()
    }
  }
}

object LoggingSlf4jSinkTest {
  val sinkLegacySlf4jImpl = LogSinkLegacySlf4jImpl(RenderingPolicy.simplePolicy())

  def setupSlf4jLogger(): IzLogger = {
    IzLogger(IzLogger.Level.Trace, sinkLegacySlf4jImpl)
  }
}
