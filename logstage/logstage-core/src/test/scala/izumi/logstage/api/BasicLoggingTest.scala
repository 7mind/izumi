package izumi.logstage.api

import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.logstage.api.Log._
import izumi.logstage.api.rendering.{RenderingOptions, StringRenderingPolicy}

import scala.util.Random
import org.scalatest.wordspec.AnyWordSpec

class BasicLoggingTest extends AnyWordSpec {

  "Argument extraction macro" should {
    "extract argument names from an arbitrary string" in {
      val arg1 = 1
      val arg2 = "argument 2"

      val message = Message(s"argument1: $arg1, argument2: $arg2, argument2 again: $arg2, expression ${2 + 2}, ${2 + 2}")
      val expectation = List(
        LogArg(Seq("arg1"), 1, hiddenName = false),
        LogArg(Seq("arg2"), "argument 2", hiddenName = false),
        LogArg(Seq("arg2"), "argument 2", hiddenName = false),
        LogArg(Seq("UNNAMED:4"), 4, hiddenName = false),
        LogArg(Seq("UNNAMED:4"), 4, hiddenName = false),
      )

      val expectedParts = List("argument1: ", ", argument2: ", ", argument2 again: ", ", expression ", ", ", "")

      assert(message.args == expectation)
      assert(message.template.parts == expectedParts)

      val message1 = Message(s"expression: ${Random.self.nextInt() + 1}")
      assert(message1.args.head.name == "EXPRESSION:scala.util.Random.self.nextInt().+(1)")
      assert(message1.template.parts == List("expression: ", ""))
    }

    "progression test: doesn't support .stripMargin yet" in {
      val m = "M E S S A G E"
      val message = Message {
        s"""This
           |is a
           |multiline ${m -> "message"}""".stripMargin
      }

      assert(message.template.parts != List("This\nis a\nmultiline ", ""))
      assert(message.args != List(LogArg(Seq("message"), m, hiddenName = false)))
    }
  }

  "String rendering policy" should {
    "not fail on unbalanced messages" in {
      val p = new StringRenderingPolicy(RenderingOptions(colored = false))
      val rendered = render(p, Message(StringContext("begin ", " end"), Seq(LogArg(Seq("[a1]"), 1, hiddenName = false), LogArg(Seq("[a2]"), 2, hiddenName = false))))
      assert(rendered.endsWith("begin [a_1]=1 end {{ [a_2]=2 }}"))
    }
  }

  "logstage" should {
    "allow constructing Log.Message" in {
      val i = 5
      val s = "hi"
      val msg = Message(s"begin $i $s end")

      assert(msg == Message(StringContext("begin ", " ", " end"), Seq(LogArg(Seq("i"), 5, hiddenName = false), LogArg(Seq("s"), "hi", hiddenName = false))))
    }
  }

  private def render(p: StringRenderingPolicy, m: Message) = {
    p.render(Entry(m, Context(StaticExtendedContext(LoggerId("test"), SourceFilePosition("test.scala", 0)), DynamicContext(Level.Warn, ThreadData("test", 0), 0), CustomContext(Seq.empty))))
  }
}
