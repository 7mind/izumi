package com.github.pshirshov.izumi.distage

import distage._
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u.TypeTag
import org.scalatest.WordSpec

import scala.language.higherKinds

class ProviderMagnetTest extends WordSpec {
  import com.github.pshirshov.izumi.distage.Fixtures.Case16._

  def priv(@Id("locargann") x: Int): Unit = {val _ = x}

  val locargannfnval: Int @Id("loctypeann") => Unit = priv
  val locargannfnvalerased = priv _

  "Annotation extracting WrappedFunction" should {
    "progression test: can't handle method reference vals, they lose annotation info" in {
      val fn = ProviderMagnet(locargannfnvalerased).get
      assert(fn.diKeys.collect{case i: DIKey.IdKey[_] => i}.isEmpty)

      val fn2 = ProviderMagnet(testVal2).get
      assert(fn2.diKeys.collect{case i: DIKey.IdKey[_] => i}.isEmpty)
    }

    "progression test: can't handle curried function values" in {
      val fn3 = ProviderMagnet(testVal3).get
      assert(fn3.diKeys.contains(DIKey.get[Long].named("valsbtypeann1")))
      assert(!fn3.diKeys.contains(DIKey.get[String].named("valsbtypeann2")))
    }

    "progression test: doesn't fail on conflicting annotations" in {
      assertCompiles("ProviderMagnet.apply(defconfannfn _)")
      assertCompiles("ProviderMagnet.apply(defconfannfn2 _)")
    }

    "produce correct DI keys for anonymous inline lambda" in {
      val fn = ProviderMagnet {
        x: Int @Id("inlinetypeann") => x
      }.get

      assert(fn.diKeys contains DIKey.get[Int].named("inlinetypeann"))
    }

    "produce correct DI keys for anonymous inline lambda with annotation parameter passed by name" in {
      val fn = ProviderMagnet {
        x: Int @Id(name = "inlinetypeann") => x
      }.get

      assert(fn.diKeys contains DIKey.get[Int].named("inlinetypeann"))
    }

    "handle anonymous inline nullarg function" in {
      assertCompiles("ProviderMagnet( () => 0 )")
      assertCompiles("ProviderMagnet{ () => 0 }")
      assertCompiles("ProviderMagnet({ () => 0 })")
      assertCompiles("ProviderMagnet({{{ () => 0 }}})")
    }

    "handle opaque local references with type annotations" in {
      def loctypeannfn(x: Int @Id("loctypeann")): Unit = {val _ = x}

      val fn = ProviderMagnet(loctypeannfn _).get

      assert(fn.diKeys contains DIKey.get[Int].named("loctypeann"))
    }

    "handle opaque local references with argument annotations" in {
      def locargannfn(@Id("locargann") x: Int): Unit = {val _ = x}

      val fn = ProviderMagnet(locargannfn _).get
      assert(fn.diKeys contains DIKey.get[Int].named("locargann"))
    }

    "can handle value references with annotated type signatures" in {
      val fn = ProviderMagnet(locargannfnval).get
      assert(fn.diKeys contains DIKey.get[Int].named("loctypeann"))

      val fn2 = ProviderMagnet(testVal).get
      assert(fn2.diKeys contains DIKey.get[String].named("valsigtypeann1"))
      assert(fn2.diKeys contains DIKey.get[Int].named("valsigtypeann2"))
    }

    "ProviderMagnet can work with vals" in {
      def triggerConversion[R](x: ProviderMagnet[R]): Int = {val _ = x; return 5}

      assertCompiles("triggerConversion(testVal3)")
    }

    "handle opaque references with type annotations" in {
      val fn = ProviderMagnet.apply(deftypeannfn _).get

      assert(fn.diKeys contains DIKey.get[String].named("deftypeann"))
      assert(fn.diKeys contains DIKey.get[Int].named("deftypeann2"))
    }

    "handle opaque references with argument annotations" in {
      val fn = ProviderMagnet.apply(defargannfn _).get

      assert(fn.diKeys contains DIKey.get[String].named("defargann"))
      assert(fn.diKeys contains DIKey.get[Int].named("defargann2"))
    }

    "handle opaque local references in traits" in {
      val testProviderModule = new TestProviderModule {}
      assert(ProviderMagnet(testProviderModule.implArg _).get.ret <:< SafeType.get[TestProviderModule#TestClass])
      assert(ProviderMagnet(testProviderModule.implType _).get.ret <:< SafeType.get[TestProviderModule#TestClass])
    }

    "handle constructor references with argument annotations" in {
      val fn = ProviderMagnet.apply(new ClassArgAnn(_, _)).get

      assert(fn.diKeys contains DIKey.get[String].named("classargann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classargann2"))
    }

    "handle constructor references with type annotations" in {
      val fn = ProviderMagnet.apply(new ClassTypeAnn(_, _)).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "handle opaque references with generic parameters" in {
      def locgenfn[T](t: T): Option[T] = Option(t)

      val fn = ProviderMagnet.apply(locgenfn[Int](_)).get

      assert(fn.diKeys contains DIKey.get[Int])
    }

    "handle opaque references with annotations and generic parameters" in {
      def locgenfn[T](@Id("x") t: T): Option[T] = Option(t)

      val fn = ProviderMagnet.apply(locgenfn[Int](_)).get

      assert(fn.diKeys contains DIKey.get[Int].named("x"))
    }

    "handle opaque lambdas with generic parameters" in {
      def locgenfn[T](@Id("x") t: T): Option[T] = Option(t)

      val fn = ProviderMagnet.apply { x: Int => locgenfn(x) }.get

      assert(fn.diKeys contains DIKey.get[Int].named("x"))
    }

    "handle constructor references with argument annotations with a lossy wrapper lambda" in {
      val fn = ProviderMagnet.apply((x, y) => new ClassArgAnn(x, y)).get

      assert(fn.diKeys contains DIKey.get[String].named("classargann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classargann2"))
    }

    "handle constructor references with type annotations with a lossy wrapper lambda" in {
      val fn = ProviderMagnet.apply((x, y) => new ClassTypeAnn(x, y)).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "progression test: FAILS to handle case class .apply references with argument annotations" in {
      val fn = ProviderMagnet.apply(ClassArgAnn.apply _).get

      assert(!fn.diKeys.contains(DIKey.get[String].named("classargann1")))
      assert(!fn.diKeys.contains(DIKey.get[Int].named("classargann2")))
    }

    "handle case class .apply references with type annotations" in {
      val fn = ProviderMagnet.apply(ClassTypeAnn.apply _).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "handle generic parameters with TypeTag" in {
      def fn[T: TypeTag]  = ProviderMagnet.apply((x: T @Id("gentypeann")) => x).get

      assert(fn[Int].diKeys contains DIKey.get[Int].named("gentypeann"))
      assert(fn[String].diKeys contains DIKey.get[String].named("gentypeann"))
    }

    "handle generic parameters with Tag" in {
      def fn[T: Tag]  = ProviderMagnet.apply((x: T @Id("gentypeann")) => x).get

      assert(fn[Int].diKeys contains DIKey.get[Int].named("gentypeann"))
      assert(fn[String].diKeys contains DIKey.get[String].named("gentypeann"))
    }

    "handle higher-kinded parameters with TagK" in {
      def fn[F[_]: TagK] = ProviderMagnet.apply((x: F[Int] @Id("gentypeann")) => x).get

      assert(fn[List].diKeys contains DIKey.get[List[Int]].named("gentypeann"))
      assert(fn[Set].diKeys contains DIKey.get[Set[Int]].named("gentypeann"))
    }

    "generic parameters without TypeTag should fail" in {
      assertTypeError(
        """def fn[T]  = ProviderMagnet.apply((x: T @Id("gentypeann")) => x).get"""
      )
    }
  }

}

