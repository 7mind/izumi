package izumi.logstage.api

import scala.annotation.nowarn
import izumi.fundamentals.platform.language.{IzScala, SourceFilePosition}
import izumi.logstage.api.Log.*
import izumi.logstage.api.rendering.{LogstageCodec, RenderingOptions, StringRenderingPolicy}
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random

@nowarn("msg=[Ee]xpression.*logger")
class BasicLoggingTest extends AnyWordSpec {

  "Argument extraction macro" should {
    "extract argument names from an arbitrary string" in {
      val arg1 = 1
      val arg2 = "argument 2"

      val message = Message(s"argument1: $arg1, argument2: $arg2, argument2 again: $arg2, expression ${2 + 2}, ${2 + 2}")

      val expectation = if (IzScala.scalaRelease.major == 3) {
        // on scala3 we get access to exact raw tree w/o optimizations
        List(
          LogArg(Seq("arg1"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("arg2"), "argument 2", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
          LogArg(Seq("arg2"), "argument 2", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
          LogArg(Seq("EXPRESSION:2.+(2)"), 4, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("EXPRESSION:2.+(2)"), 4, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        )
      } else {
        List(
          LogArg(Seq("arg1"), 1, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("arg2"), "argument 2", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
          LogArg(Seq("arg2"), "argument 2", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
          LogArg(Seq("UNNAMED:4"), 4, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
          LogArg(Seq("UNNAMED:4"), 4, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
        )
      }

      val expectedParts = List("argument1: ", ", argument2: ", ", argument2 again: ", ", expression ", ", ", "")

      assert(message.args == expectation)
      assert(message.template.parts == expectedParts)

      val message1 = Message(s"expression: ${Random.self.nextInt() + 1}")
      assert(message1.args.head.name == "EXPRESSION:scala.util.Random.self.nextInt().+(1)")
      assert(message1.template.parts == List("expression: ", ""))
    }

    "support .stripMargin" in {
      val m = "M E S S A G E"
      val message1 = Message(
        s"""This
           |is a
           |multiline ${m -> "message"}""".stripMargin
      )
      assert(message1.template.parts.toList == List("This\nis a\nmultiline ", ""))
      assert(message1.args == List(LogArg(Seq("message"), m, hiddenName = false, Some(LogstageCodec.LogstageCodecString))))

      val message2 = Message("single line with stripMargin".stripMargin)
      assert(message2.template.parts.toList == List("single line with stripMargin"))
      assert(message2.args == List.empty)

      val message3 = Message {
        """Hello
          |there!
          |""".stripMargin
      }
      assert(message3.template.parts.toList == List("Hello\nthere!\n"))
      assert(message3.args == List.empty)
    }
  }

  "String rendering policy" should {
    "not fail on unbalanced messages" in {
      val p = new StringRenderingPolicy(RenderingOptions.default.copy(colored = false), None)
      val rendered =
        render(p, Message(StringContext("begin ", " end"), Seq(LogArg(Seq("[a1]"), 1, hiddenName = false, None), LogArg(Seq("[a2]"), 2, hiddenName = false, None))))
      assert(rendered.endsWith("begin [a_1]=1 end {{ [a_2]=2 }}"))
    }
  }

  "logstage" should {
    "allow constructing Log.Message" in {
      val i = 5
      val s = "hi"
      val msg = Message(s"begin $i $s end")

      assert(
        msg == Message(
          StringContext("begin ", " ", " end"),
          Seq(
            LogArg(Seq("i"), 5, hiddenName = false, Some(LogstageCodec.LogstageCodecInt)),
            LogArg(Seq("s"), "hi", hiddenName = false, Some(LogstageCodec.LogstageCodecString)),
          ),
        )
      )
    }
    "allow concatenating Log.Message" should {
      "multiple parts" in {
        val msg1 = Message(s"begin1${1.1}middle1${1.2}end1")
        val msg2 = Message(s"begin2 ${2.1} middle2 ${2.2} end2 ")
        val msg3 = Message(s" begin3${3.1}middle3 ${3.2}end3")

        val msgConcatenated = msg1 + msg2 + msg3

        assert(
          msgConcatenated.template.parts == Seq(
            "begin1",
            "middle1",
            "end1begin2 ",
            " middle2 ",
            " end2  begin3",
            "middle3 ",
            "end3",
          )
        )

        assert(msgConcatenated.args.map(_.value) == Seq(1.1, 1.2, 2.1, 2.2, 3.1, 3.2))
      }
      "one part" in {
        val msg1 = Message(s"begin1")
        val msg2 = Message(s"${2}")
        val msg3 = Message(s"end3")

        val msgConcatenated = msg1 + msg2 + msg3

        assert(
          msgConcatenated.template.parts == Seq(
            "begin1",
            "end3",
          )
        )
      }
      "zero parts" in {
        val msgOnePart = Message(s"onePart")
        val msgZeroParts = Message("")
        val msgEmpty = Message.empty

        assert(
          (msgOnePart + msgZeroParts).template.parts == Seq(
            "onePart"
          )
        )
        assert(
          (msgZeroParts + msgOnePart).template.parts == Seq(
            "onePart"
          )
        )

        assert(
          (msgOnePart + msgEmpty).template.parts == Seq(
            "onePart"
          )
        )
        assert(
          (msgEmpty + msgOnePart).template.parts == Seq(
            "onePart"
          )
        )

        assert((msgEmpty + msgEmpty).template.parts == Seq(""))
        assert((msgZeroParts + msgZeroParts).template.parts == Seq(""))
        assert((msgEmpty + msgZeroParts).template.parts == Seq(""))
        assert((msgZeroParts + msgEmpty).template.parts == Seq(""))
      }
      "empty StringContext" in {
        val msgEmptyStringContext = Message(StringContext(), Nil)
        val msgOnePart = Message(s"onePart")

        assert(
          (msgOnePart + msgEmptyStringContext).template.parts == Seq(
            "onePart"
          )
        )
        assert(
          (msgEmptyStringContext + msgOnePart).template.parts == Seq(
            "onePart"
          )
        )
      }
    }
  }

  private def render(p: StringRenderingPolicy, m: Message) = {
    p.render(
      Entry(
        m,
        Context(
          StaticExtendedContext(LoggerId("test"), SourceFilePosition("test.scala", 0)),
          DynamicContext(Level.Warn, ThreadData("test", 0), 0),
          CustomContext(Seq.empty),
        ),
      )
    )
  }
}
