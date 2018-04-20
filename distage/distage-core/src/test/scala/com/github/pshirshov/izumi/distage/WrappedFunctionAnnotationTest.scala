package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures.WrappedFunctionAnnotationTest._
import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import org.scalatest.WordSpec

class WrappedFunctionAnnotationTest extends WordSpec {
  def priv(@Id("locargann") x: Int): Unit = ()

  val locargannfnval: Int => Unit = priv _

  "Annotation extracting WrappedFunction" should {

    "produce correct DI keys for anonymous inline lambda" in {
      assertCompiles("""
      DIKeyWrappedFunction {
        x: Int @Id("inlinetypeann") => x
      }
      """)
    }

    "produce correct DI keys for anonymous inline lambda with annotation parameter passed by name" in {
      assertCompiles("""
      DIKeyWrappedFunction {
        x: Int @Id(name = "inlinetypeann") => x
      }
      """)
    }

    "handle opaque local references with type annotations" in {
      def loctypeannfn (x: Int @Id("loctypeann")): Unit = ()

      assertCompiles("DIKeyWrappedFunction(loctypeannfn _)")
    }

    "handle opaque local references with argument annotations" in {
      def locargannfn(@Id("locargann") x: Int): Unit = ()

      assert(DIKeyWrappedFunction(locargannfn _).diKeys contains RuntimeDIUniverse.DIKey.IdKey(RuntimeDIUniverse.SafeType.get[Int], "locargann"))
//      assert(DIKeyWrappedFunction(locargannfnval).diKeys contains RuntimeDIUniverse.DIKey.IdKey(RuntimeDIUniverse.SafeType.get[Int], "locargann"))
    }

    "wrappedfunction can work with vals" in {
      def triggerConversion[R](x : WrappedFunction[R]): Int = 5

      assertCompiles("triggerConversion(testVal3)")
    }

    "regression test: dikeywrappedfunction can't work with vals yet" in {
      def triggerConversion[R](x : DIKeyWrappedFunction[R]): Int = 5

      assertTypeError("triggerConversion(testVal3)")
    }

    "handle opaque references with type annotations" in {
      assertCompiles("DIKeyWrappedFunction.apply(deftypeannfn _)")
    }

    "handle opaque references with argument annotations" in {
      assertCompiles("DIKeyWrappedFunction.apply(defargannfn _)")
    }

    "fail on conflicting annotations" in {
      assertTypeError("DIKeyWrappedFunction.apply(defconfannfn _)")
      assertTypeError("DIKeyWrappedFunction.apply(defconfannfn2 _)")

    }

    "can't handle value annotations in the type signature" in {
      assertTypeError("DIKeyWrappedFunction.apply(testVal)")
      assertTypeError("DIKeyWrappedFunction.apply(testVal2)")
      assertTypeError("DIKeyWrappedFunction.apply(testVal3)")
    }

    "handle opaque local references in traits" in {
      val testProviderModule = new TestProviderModule()
      assert(DIKeyWrappedFunction(testProviderModule.implArg _).ret <:< RuntimeDIUniverse.SafeType.get[TestProviderModule#TestClass])
      assert(DIKeyWrappedFunction(testProviderModule.implType _).ret <:< RuntimeDIUniverse.SafeType.get[TestProviderModule#TestClass])
    }
  }

}

