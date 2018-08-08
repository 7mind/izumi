package com.github.pshirshov.izumi.logstage.api.rendering.json

import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.sink.{ConsoleSink, ExampleService}
import org.scalatest.WordSpec

class LoggingJson4sTest extends WordSpec {

  import LoggingJson4sTest._

  "Log macro" should {
    "support console sink with json output policy" in {
      new ExampleService(setupJsonLogger()).start()
    }
  }
}


object LoggingJson4sTest {

  val jsonPolicy = new JsonRenderingPolicy(prettyPrint = true)
  val consoleSinkJson = new ConsoleSink(jsonPolicy)

  def setupJsonLogger(): IzLogger = {
    IzLogger.make(consoleSinkJson)
  }


}
