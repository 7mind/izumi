package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import org.scalatest.WordSpec

class WrappedFunctionAnnotationTest extends WordSpec {

  "WrappedFunction with annotation macro" should {
    "produce correct DI keys for anonymous inline lambda" in {
      DIKeyWrappedFunction {
        x: Int @Id("hey") => x
      }
    }

    "emit warning on opaque references" in {
      def fn(x: Int @Id("hey")) = ()

      DIKeyWrappedFunction(fn _)
    }

    "handle opaque references" in {
      import WrappedFunctionAnnotationTest._

      DIKeyWrappedFunction(testFn _)
    }

    "handle lambda type annotations" in {
      import WrappedFunctionAnnotationTest._

      DIKeyWrappedFunction(testVal)
    }

    "handle opaque lambda references" in {
      import WrappedFunctionAnnotationTest._

      DIKeyWrappedFunction(testVal2)
    }

    "fail on annotations diverging in typesignature and param list" in {
      import WrappedFunctionAnnotationTest._

      DIKeyWrappedFunction(testVal3)
    }
  }
}

object WrappedFunctionAnnotationTest {
  def testFn(y: String @Id("z")): String = y

  val testVal: String @Id("t") => String = identity

  val testVal2: Boolean => String = { x: Boolean @Id("id") => x.toString }

  val testVal3: Long @Id("id1") => Long = { x: Long @Id("id2") => x }
}
