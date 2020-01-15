package izumi.logstage.api.rendering.json

import izumi.logstage.api.{IzLogger, TestSink}
import izumi.logstage.sink.{ConsoleSink, ExampleService}
import io.circe.literal._
import io.circe.parser._
import org.scalatest.wordspec.AnyWordSpec

class LogstageCirceRenderingTest extends AnyWordSpec {

  val debug = false

  "Log macro" should {
    "support console sink with json output policy" in {
      val (logger, sink) = setupJsonLogger(debug)
      val jsonc = json"""{"customctx": 1}"""
      val jsonv = json"""{"custommessage": 2}"""
      logger("ctx" -> "something", "jctx" -> jsonc).debug(s"Example message $jsonv")

      new ExampleService(logger).start()

      val renderedMessages = sink.fetchRendered()
      assert(renderedMessages.nonEmpty)

      renderedMessages.foreach{
        s =>
          parse(s) match {
            case Left(value) =>
              fail(value)
            case Right(value) =>
              value.asObject.map(_.toMap) match {
                case Some(o) =>
                  assert(o.contains("meta"))
                  assert(o.contains("text"))
                case None =>
                  fail(s"not an object: $value")
              }

          }
      }

    }

  }


  def setupJsonLogger(debug: Boolean): (IzLogger, TestSink) = {
    val jsonPolicy = new LogstageCirceRenderingPolicy(prettyPrint = true)
    val testSink = new TestSink(Some(jsonPolicy))

    val sinks = if (debug) {
      Seq(testSink, new ConsoleSink(jsonPolicy))
    } else {
      Seq(testSink)
    }

    (IzLogger(IzLogger.Level.Trace, sinks), testSink)
  }

}


