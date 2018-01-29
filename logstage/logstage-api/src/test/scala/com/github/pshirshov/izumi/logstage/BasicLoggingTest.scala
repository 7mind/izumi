package com.github.pshirshov.izumi.logstage

import com.github.pshirshov.izumi.logstage.api.ExtratingStringInterpolator
import org.scalatest.WordSpec

import scala.util.Random

class BasicLoggingTest extends WordSpec {

  "Argument extraction macro" should {
    "extract argument names from an arbitrary string" in {
      val arg1 = 1
      val arg2 = "argument 2"

      import ExtratingStringInterpolator._

      val message = m"argument1: $arg1, argument2: $arg2, argument2 again: $arg2, expression ${2+2}, ${2+2}"
      assert(message.args == List(("arg1",1), ("arg2","argument 2"), ("arg2","argument 2"), ("UNNAMED:4",4), ("UNNAMED:4",4)))
      assert(message.template.parts == List("argument1: ", ", argument2: ", ", argument2 again: ", ", expression ", ", ", ""))

      val message1 = m"expression: ${Random.self.nextInt()}"
      assert(message1.args.head._1 == "EXPRESSION:scala.util.Random.self.nextInt()")
      assert(message1.template.parts == List("expression: ", ""))
    }
  }


}


