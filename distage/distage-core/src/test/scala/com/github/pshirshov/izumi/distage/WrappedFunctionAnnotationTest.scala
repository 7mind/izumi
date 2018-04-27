package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures.Case16._
import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import org.scalatest.WordSpec

class WrappedFunctionAnnotationTest extends WordSpec {
  def priv(@Id("locargann") x: Int): Unit = ()

  val locargannfnval: Int => Unit = priv _

  "Annotation extracting WrappedFunction" should {

    "produce correct DI keys for anonymous inline lambda" in {
      val fn = DIKeyWrappedFunction {
        x: Int @Id("inlinetypeann") => x
      }

      assert(fn.diKeys contains DIKey.get[Int].named("inlinetypeann"))
    }

    "produce correct DI keys for anonymous inline lambda with annotation parameter passed by name" in {
      val fn = DIKeyWrappedFunction {
        x: Int @Id(name = "inlinetypeann") => x
      }

      assert(fn.diKeys contains DIKey.get[Int].named("inlinetypeann"))
    }

    "handle opaque local references with type annotations" in {
      def loctypeannfn(x: Int @Id("loctypeann")): Unit = ()

      val fn = DIKeyWrappedFunction(loctypeannfn _)

      assert(fn.diKeys contains DIKey.get[Int].named("loctypeann"))

    }

    "handle opaque local references with argument annotations" in {
      def locargannfn(@Id("locargann") x: Int): Unit = ()

      val fn = DIKeyWrappedFunction(locargannfn _)
      assert(fn.diKeys contains DIKey.get[Int].named("locargann"))

//      val fn = DIKeyWrappedFunction(locargannfnval)
//      assert(fn.diKeys contains DIKey.get[Int].named("locargann"))
    }

    "wrappedfunction can work with vals" in {
      def triggerConversion[R](x: WrappedFunction[R]): Int = 5

      assertCompiles("triggerConversion(testVal3)")
    }

    "regression test: dikeywrappedfunction can't work with vals yet" in {
      def triggerConversion[R](x: DIKeyWrappedFunction[R]): Int = 5

      assertTypeError("triggerConversion(testVal3)")
    }

    "handle opaque references with type annotations" in {
      val fn = DIKeyWrappedFunction.apply(deftypeannfn _)

      assert(fn.diKeys contains DIKey.get[String].named("deftypeann"))
      assert(fn.diKeys contains DIKey.get[Int].named("deftypeann2"))
    }

    "handle opaque references with argument annotations" in {
      val fn = DIKeyWrappedFunction.apply(defargannfn _)

      assert(fn.diKeys contains DIKey.get[String].named("defargann"))
      assert(fn.diKeys contains DIKey.get[Int].named("defargann2"))
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
      val testProviderModule = new TestProviderModule {}
      assert(DIKeyWrappedFunction(testProviderModule.implArg _).ret <:< SafeType.get[TestProviderModule#TestClass])
      assert(DIKeyWrappedFunction(testProviderModule.implType _).ret <:< SafeType.get[TestProviderModule#TestClass])
    }
  }

}

