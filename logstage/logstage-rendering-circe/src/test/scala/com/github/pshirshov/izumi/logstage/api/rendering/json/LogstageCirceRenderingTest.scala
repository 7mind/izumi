package com.github.pshirshov.izumi.logstage.api.rendering.json

import com.github.pshirshov.izumi.logstage.api.{IzLogger, TestSink}
import com.github.pshirshov.izumi.logstage.sink.{ConsoleSink, ExampleService}
import io.circe.parser._
import org.scalatest.WordSpec

class LogstageCirceRenderingTest extends WordSpec {

  val debug = true

  "Log macro" should {
    "support console sink with json output policy" in {
      val (logger, sink) = setupJsonLogger(debug)
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


