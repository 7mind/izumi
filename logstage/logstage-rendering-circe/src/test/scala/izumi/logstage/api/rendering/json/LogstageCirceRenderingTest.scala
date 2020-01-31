package izumi.logstage.api.rendering.json

import io.circe.Codec
import io.circe.literal._
import io.circe.parser._
import izumi.logstage.api.rendering.{LogstageCodec, LogstageWriter}
import izumi.logstage.api.strict.IzStrictLogger
import izumi.logstage.api.{IzLogger, TestSink}
import izumi.logstage.sink.{ConsoleSink, ExampleService}
import org.scalatest.wordspec.AnyWordSpec


class LogstageCirceRenderingTest extends AnyWordSpec {
  import LogstageCirceRenderingTest._

  val debug = true

  "Log macro" should {
    "support console sink with json output policy" in {
      val (logger, sink) = setupJsonLogger(debug)
      val jsonc = json"""{"customctx": 1}"""
      val jsonv = json"""{"custommessage": 2}"""
      logger("ctx" -> "something", "jctx" -> jsonc).debug(s"Example message $jsonv")

      new ExampleService(logger).start()

      val renderedMessages = sink.fetchRendered()
      assert(renderedMessages.nonEmpty)

      renderedMessages.foreach {
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

    "support strict logging" in {
      val (logger, sink) = setupJsonStrictLogger(debug)
      logger.info(s"${List("a", "b", "c") -> "list"}, ${WithCustomCodec() -> "custom"}, ${WithCustomDerivedCodec(42, "hatersgonnahate", List(Map("kudah" -> "kukarek"))) -> "custom1"}")
      val renderedMessages = sink.fetchRendered()
      assert(renderedMessages.nonEmpty)
      val data = parse(renderedMessages.head).right.get.hcursor.downField("event").focus.get.asObject.map(_.toMap).get

      assert(data("custom") == json"""{"a":[1,"b"]}""")
      assert(data("list") == json"""["a","b","c"]""")
      assert(data("custom_1") == json"""{"a":42,"b":"hatersgonnahate","c":[{"kudah":"kukarek"}]}""")
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

  def setupJsonStrictLogger(debug: Boolean): (IzStrictLogger, TestSink) = {
    val jsonPolicy = new LogstageCirceRenderingPolicy(prettyPrint = true)
    val testSink = new TestSink(Some(jsonPolicy))

    val sinks = if (debug) {
      Seq(testSink, new ConsoleSink(jsonPolicy))
    } else {
      Seq(testSink)
    }

    (IzStrictLogger(IzLogger.Level.Trace, sinks), testSink)
  }
}


object LogstageCirceRenderingTest {
  case class WithCustomCodec()

  object WithCustomCodec {
    implicit val Codec: LogstageCodec[WithCustomCodec] = (_: WithCustomCodec, writer: LogstageWriter) => {
      writer.openMap()
      writer.write("a")
      writer.openList()
      writer.write(1)
      writer.write("b")
      writer.closeList()
      writer.closeMap()
    }
  }

  case class WithCustomDerivedCodec(a: Int, b: String, c: List[Map[String, String]])

  object WithCustomDerivedCodec {
    implicit val JsonCodec: Codec.AsObject[WithCustomDerivedCodec] = _root_.io.circe.derivation.deriveCodec[WithCustomDerivedCodec]
    implicit val LsCodec: LogstageCodec[WithCustomDerivedCodec] = _root_.logstage.circe.fromCirce[WithCustomDerivedCodec]
  }

}