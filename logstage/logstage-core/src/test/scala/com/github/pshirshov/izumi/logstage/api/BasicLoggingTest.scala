package com.github.pshirshov.izumi.logstage.api

import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.logstage.api.Log._
import com.github.pshirshov.izumi.logstage.api.rendering.{RenderingOptions, StringRenderer}
import org.scalatest.WordSpec

import scala.util.Random

class BasicLoggingTest extends WordSpec {

  "Argument extraction macro" should {
    "extract argument names from an arbitrary string" in {
      val arg1 = 1
      val arg2 = "argument 2"

      val message = Message(s"argument1: $arg1, argument2: $arg2, argument2 again: $arg2, expression ${2 + 2}, ${2 + 2}")
      assert(message.args ==
        List(
          LogArg(Seq("arg1"), 1, hidden = false),
          LogArg(Seq("arg2"), "argument 2", hidden = false),
          LogArg(Seq("arg2"), "argument 2", hidden = false),
          LogArg(Seq("UNNAMED:4"), 4, hidden = false),
          LogArg(Seq("UNNAMED:4"), 4, hidden = false)
        )
      )
      assert(message.template.parts == List("argument1: ", ", argument2: ", ", argument2 again: ", ", expression ", ", ", ""))

      val message1 = Message(s"expression: ${Random.self.nextInt() + 1}")
      assert(message1.args.head.name == "EXPRESSION:scala.util.Random.self.nextInt().+(1)")
      assert(message1.template.parts == List("expression: ", ""))
    }
  }

  "String rendering policy" should {
    "not fail on unbalanced messages" in {
      val p = new StringRenderer(RenderingOptions(withColors = false))
      val rendered = render(p, Message(StringContext("begin ", " end"), Seq(LogArg(Seq("[a1]"), 1, hidden = false), LogArg(Seq("[a2]"), 2, hidden = false))))
      assert(rendered.endsWith("begin [a1]=1 end; [a2]=2"))
    }
  }

  "logstage" should {
    "allow constructing Log.Message" in {
      val i = 5
      val s = "hi"
      val msg = Message(s"begin $i $s end")

      assert(msg == Message(StringContext("begin ", " ", " end"), Seq(LogArg(Seq("i"), 5, hidden = false), LogArg(Seq("s"), "hi", hidden = false))))
    }
  }

  private def render(p: StringRenderer, m: Message) = {
    p.render(Entry(m, Context(StaticExtendedContext(LoggerId("test"), SourceFilePosition("test.scala", 0)), DynamicContext(Level.Warn, ThreadData("test", 0), 0), CustomContext(Seq.empty))))
  }
}
