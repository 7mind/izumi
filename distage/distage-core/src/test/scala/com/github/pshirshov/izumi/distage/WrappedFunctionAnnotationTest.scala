package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures.Case16._
import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import org.scalatest.WordSpec

class WrappedFunctionAnnotationTest extends WordSpec {
  def priv(@Id("locargann") x: Int): Unit = {val _ = x}

  val locargannfnval: Int @Id("locargann") => Unit = priv

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

    "handle anonymous inline nullarg function" in {
      assertCompiles("DIKeyWrappedFunction( () => 0 )")
      assertCompiles("DIKeyWrappedFunction{ () => 0 }")
      assertCompiles("DIKeyWrappedFunction({ () => 0 })")
      assertCompiles("DIKeyWrappedFunction({{{ () => 0 }}})")
    }

    "handle opaque local references with type annotations" in {
      def loctypeannfn(x: Int @Id("loctypeann")): Unit = {val _ = x}

      val fn = DIKeyWrappedFunction(loctypeannfn _)

      assert(fn.diKeys contains DIKey.get[Int].named("loctypeann"))

    }

    "handle opaque local references with argument annotations" in {
      def locargannfn(@Id("locargann") x: Int): Unit = {val _ = x}

      val fn = DIKeyWrappedFunction(locargannfn _)
      assert(fn.diKeys contains DIKey.get[Int].named("locargann"))
    }

    "progression test: value references lose annotation info" in {
      val fn1 = DIKeyWrappedFunction(locargannfnval)
      assert(fn1.diKeys contains DIKey.get[Int])
    }

    "wrappedfunction can work with vals" in {
      def triggerConversion[R](x: WrappedFunction[R]): Int = {val _ = x; return 5}

      assertCompiles("triggerConversion(testVal3)")
    }

    "dikeywrappedfunction can work with vals" in {
      def triggerConversion[R](x: DIKeyWrappedFunction[R]): Int = {val _ = x; return 5}

      assertCompiles("triggerConversion(testVal3)")
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

    "progression test: can't handle dikeys in the val type signature" in {
      assert(DIKeyWrappedFunction(testVal).diKeys.collect{case i: DIKey.IdKey[_] => i}.isEmpty)
      assert(DIKeyWrappedFunction(testVal2).diKeys.collect{case i: DIKey.IdKey[_] => i}.isEmpty)
      assert(DIKeyWrappedFunction(testVal3).diKeys.collect{case i: DIKey.IdKey[_] => i}.isEmpty)
    }

    "handle opaque local references in traits" in {
      val testProviderModule = new TestProviderModule {}
      assert(DIKeyWrappedFunction(testProviderModule.implArg _).ret <:< SafeType.get[TestProviderModule#TestClass])
      assert(DIKeyWrappedFunction(testProviderModule.implType _).ret <:< SafeType.get[TestProviderModule#TestClass])
    }
  }

}

