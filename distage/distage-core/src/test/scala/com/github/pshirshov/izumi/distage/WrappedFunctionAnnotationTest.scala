package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures.Case16._
import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.functions.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import org.scalatest.WordSpec

class WrappedFunctionAnnotationTest extends WordSpec {
  def priv(@Id("locargann") x: Int): Unit = {val _ = x}

  val locargannfnval: Int @Id("loctypeann") => Unit = priv
  val locargannfnvalerased = priv _

  "Annotation extracting WrappedFunction" should {
    "progression test: can't handle method reference vals, they lose annotation info" in {
      val fn = DIKeyWrappedFunction(locargannfnvalerased)
      assert(fn.diKeys.collect{case i: DIKey.IdKey[_] => i}.isEmpty)

      val fn2 = DIKeyWrappedFunction(testVal2)
      assert(fn2.diKeys.collect{case i: DIKey.IdKey[_] => i}.isEmpty)
    }

    "progression test: can't handle curried function values" in {
      val fn3 = DIKeyWrappedFunction(testVal3)
      assert(fn3.diKeys.contains(DIKey.get[Long].named("valsbtypeann1")))
      assert(!fn3.diKeys.contains(DIKey.get[String].named("valsbtypeann2")))
    }

    "progression test: doesn't fail on conflicting annotations" in {
      assertCompiles("DIKeyWrappedFunction.apply(defconfannfn _)")
      assertCompiles("DIKeyWrappedFunction.apply(defconfannfn2 _)")
    }

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

    "can handle value references with annotated type signatures" in {
      val fn = DIKeyWrappedFunction(locargannfnval)
      assert(fn.diKeys contains DIKey.get[Int].named("loctypeann"))

      val fn2 = DIKeyWrappedFunction(testVal)
      assert(fn2.diKeys contains DIKey.get[String].named("valsigtypeann1"))
      assert(fn2.diKeys contains DIKey.get[Int].named("valsigtypeann2"))
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

    "handle opaque local references in traits" in {
      val testProviderModule = new TestProviderModule {}
      assert(DIKeyWrappedFunction(testProviderModule.implArg _).ret <:< SafeType.get[TestProviderModule#TestClass])
      assert(DIKeyWrappedFunction(testProviderModule.implType _).ret <:< SafeType.get[TestProviderModule#TestClass])
    }
  }

}

