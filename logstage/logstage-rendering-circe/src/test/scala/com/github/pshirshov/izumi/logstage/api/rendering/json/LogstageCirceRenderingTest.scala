package com.github.pshirshov.izumi.logstage.api.rendering.json

import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.sink.{ConsoleSink, ExampleService}
import org.scalatest.WordSpec

class LogstageCirceRenderingTest extends WordSpec {

  import LogstageCirceRenderingTest._

  "Log macro" should {
    "support console sink with json output policy" in {
      new ExampleService(setupJsonLogger()).start()
    }

  }
}


object LogstageCirceRenderingTest {

  val jsonPolicy = new LogstageCirceRenderer(prettyPrint = true)
  val consoleSinkJson = new ConsoleSink(jsonPolicy)

  def setupJsonLogger(): IzLogger = {
    IzLogger(IzLogger.Level.Trace, consoleSinkJson)
  }

}
