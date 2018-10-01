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

    "afg " in {
      {
        import logstage.IzLogger
        import logstage.ConsoleSink
        import logstage._
        import logstage.circe._

        val jsonSink = ConsoleSink.json(prettyPrint = true)
        val textSink = ConsoleSink(colored = true)

        val sinks = List(jsonSink, textSink)

        val logger: IzLogger = IzLogger(Trace, sinks)
        val contextLogger: IzLogger = logger(Map("key" -> "value"))

        logger.error("Hey")
        // E 2018-10-01T17:02:54.628+01:00[Europe/Dublin] c.g.p.i.l.a.r.j.LogstageCirceRenderingTest.<local LogstageCirceRenderingTest> ...deringTest:1  (LogstageCirceRenderingTest.scala:35) Hey; @type=const

        contextLogger.error(s"Hey")
        // E 2018-10-01T17:02:54.810+01:00[Europe/Dublin] c.g.p.i.l.a.r.j.LogstageCirceRenderingTest.<local LogstageCirceRenderingTest> ...deringTest:1  (LogstageCirceRenderingTest.scala:36) {key=value} Hey

      }


    }
  }
}


object LogstageCirceRenderingTest {

  val jsonPolicy = new LogstageCirceRenderingPolicy(prettyPrint = true)
  val consoleSinkJson = new ConsoleSink(jsonPolicy)

  def setupJsonLogger(): IzLogger = {
    IzLogger(IzLogger.Level.Trace, consoleSinkJson)
  }

}
