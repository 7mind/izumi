package izumi.distage.impl

import distage.{Id, Tag, TagK}
import izumi.distage.constructors.ClassConstructor
import izumi.distage.fixtures.BasicCases.BasicCase4.ClassTypeAnnT
import izumi.distage.fixtures.BasicCases.BasicCase7
import izumi.distage.fixtures.ProviderCases.ProviderCase1
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{DIKey, SafeType, TypedRef}
import izumi.fundamentals.platform.build.MacroParameters
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.ScalaRelease
import izumi.fundamentals.platform.language.Quirks.*
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class FunctoidTest extends AnyWordSpec {
  import ProviderCase1._

  def priv(@Id("locargann") x: Int): Unit = x.discard()

  val locargannfnval: Int @Id("loctypeann") => Unit = priv
  val locargannfnvalerased: Int => Unit = priv

  "Annotation extracting WrappedFunction" should {
    "can't handle opaque function vals, that hide underlying method reference" in {
      val fn = Functoid(locargannfnvalerased).get
      assert(fn.diKeys.collect { case i: DIKey.IdKey[?] => i }.isEmpty)

      val fn2 = Functoid(testVal2).get
      assert(fn2.diKeys.collect { case i: DIKey.IdKey[?] => i }.isEmpty)
    }

    "produce correct DI keys for anonymous inline lambda" in {
      val fn = Functoid {
        (x: Int @Id("inlinetypeann")) => x
      }.get

      assert(fn.diKeys contains DIKey.get[Int].named("inlinetypeann"))
    }

    "produce correct DI keys for anonymous inline lambda with annotation parameter passed by name" in {
      val fn = Functoid {
        (x: Int @Id(name = "inlinetypeann")) => x
      }.get

      assert(fn.diKeys contains DIKey.get[Int].named("inlinetypeann"))
    }

    "handle anonymous inline nullarg function" in {
      assertCompiles("Functoid( () => 0 )")
      assertCompiles("Functoid{ () => 0 }")
      assertCompiles("Functoid({ () => 0 })")
      assertCompiles("Functoid({{{ () => 0 }}})")
    }

    "handle opaque local references with type annotations" in {
      def loctypeannfn(x: Int @Id("loctypeann")): Unit = { val _ = x }

      val fn = Functoid(loctypeannfn _).get

      assert(fn.diKeys contains DIKey.get[Int].named("loctypeann"))
    }

    "handle opaque local references with argument annotations" in {
      def locargannfn(@Id("locargann") x: Int): Unit = { val _ = x }

      val fn = Functoid(locargannfn _).get
      assert(fn.diKeys contains DIKey.get[Int].named("locargann"))
    }

    "can handle value references with annotated type signatures" in {
      val fn = Functoid(locargannfnval).get
      assert(fn.diKeys contains DIKey.get[Int].named("loctypeann"))

      val fn2 = Functoid(testVal).get
      assert(fn2.diKeys contains DIKey.get[String].named("valsigtypeann1"))
      assert(fn2.diKeys contains DIKey.get[Int].named("valsigtypeann2"))
    }

    "can handle local value references with annotated type signatures" in {
      val fnval: Int @Id("loctypeann") => Unit = _.discard()
      val fn = Functoid(fnval).get

      assert(fn.diKeys contains DIKey.get[Int].named("loctypeann"))

      def defval(z: String): Int @Id("loctypeann") => Unit = discard(z, _)
      val fn1 = Functoid(defval("x")).get

      assert(fn1.diKeys contains DIKey.get[Int].named("loctypeann"))

      def defdef(z: String)(x: Int @Id("locargann")): Unit = discard(z, x)
      val fn2 = Functoid(defdef("x") _).get

      assert(fn2.diKeys contains DIKey.get[Int].named("locargann"))
    }

    "handle references with annotated type signatures, if a function value is curried, the result is the next function" in {
      val fn = Functoid(testVal3).get

      assert(fn.diKeys.contains(DIKey.get[Long].named("valsbtypeann1")))
      assert(!fn.diKeys.contains(DIKey.get[String].named("valsbtypeann2")))
      assert(fn.ret == SafeType.get[String @Id("valsbtypeann2") => Long])
    }

    "Functoid can work with vals" in {
      def triggerConversion[R](x: Functoid[R]): Int = { val _ = x; return 5 }

      assert(triggerConversion(testVal3) == 5)
    }

    "handle opaque references with type annotations" in {
      val fn = Functoid.apply(deftypeannfn _).get

      assert(fn.diKeys contains DIKey.get[String].named("deftypeann"))
      assert(fn.diKeys contains DIKey.get[Int].named("deftypeann2"))
    }

    "handle opaque by-name references with type annotations" in {
      val fn = Functoid.apply(deftypeannfnbyname _).get

      assert(fn.diKeys contains DIKey.get[String].named("deftypeann"))
      assert(fn.diKeys contains DIKey.get[Int].named("deftypeann2"))
    }

    "handle opaque references with argument annotations" in {
      val fn = Functoid.apply(defargannfn _).get

      assert(fn.diKeys contains DIKey.get[String].named("defargann"))
      assert(fn.diKeys contains DIKey.get[Int].named("defargann2"))
    }

    "handle opaque references with argument annotations 2" in {
      val fn = Functoid.apply(defargannfn(_, _)).get

      assert(fn.diKeys contains DIKey.get[String].named("defargann"))
      assert(fn.diKeys contains DIKey.get[Int].named("defargann2"))
    }

    "handle polymorphic functions" in {
      val fn1 = Functoid.apply(poly[List] _).get

      assert(fn1.diKeys.headOption contains DIKey.get[List[Int]])
      assert(fn1.ret == SafeType.get[List[Unit] => Poly[List]])

      val fn2 = Functoid.apply(poly[List](List(1))(_)).get

      assert(fn2.diKeys.headOption contains DIKey.get[List[Unit]])
      assert(fn2.ret == SafeType.get[Poly[List]])
    }

    "handle polymorphic function returns" in {
      val fn = Functoid.apply(poly[List](List(1))).get

      assert(fn.diKeys.headOption contains DIKey.get[List[Unit]])
      assert(fn.ret == SafeType.get[Poly[List]])
    }

    "handle opaque local references in traits" in {
      val testProviderModule = new TestProviderModule {}
      assert(Functoid(testProviderModule.implArg _).get.ret <:< SafeType.get[testProviderModule.TestClass])
      assert(Functoid(testProviderModule.implType _).get.ret <:< SafeType.get[testProviderModule.TestClass])
      // type projections broken
      //      assert(Functoid(testProviderModule.implArg _).get.ret <:< SafeType.get[TestProviderModule#TestClass])
      //      assert(Functoid(testProviderModule.implType _).get.ret <:< SafeType.get[TestProviderModule#TestClass])
    }

    "handle constructor references with argument annotations" in {
      val fn = Functoid.apply(new ClassArgAnn(_, _)).get

      assert(fn.diKeys contains DIKey.get[String].named("classargann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classargann2"))
    }

    "handle constructor references with type annotations" in {
      val fn = Functoid.apply(new ClassTypeAnn(_, _)).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "handle opaque references with generic parameters" in {
      def locgenfn[T](t: T): Option[T] = Option(t)

      val fn = Functoid.apply(locgenfn[Int](_)).get

      assert(fn.diKeys contains DIKey.get[Int])
    }

    "handle opaque references with annotations and generic parameters" in {
      def locgenfn[T](@Id("x") t: T): Option[T] = Option(t)

      val fn = Functoid.apply(locgenfn[Int](_)).get

      assert(fn.diKeys contains DIKey.get[Int].named("x"))
    }

    "handle opaque lambdas with generic parameters" in {
      def locgenfn[T](@Id("x") t: T): Option[T] = Option(t)

      val fn = Functoid.apply {
        (x: Int) => locgenfn(x)
      }.get

      assert(fn.diKeys contains DIKey.get[Int].named("x"))
    }

    "handle constructor references with argument annotations with a lossy wrapper lambda" in {
      val fn = Functoid.apply((x, y) => new ClassArgAnn(x, y)).get

      assert(fn.diKeys contains DIKey.get[String].named("classargann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classargann2"))
    }

    "handle constructor references with by-name type annotations with a lossy wrapper lambda" in {
      val fn = Functoid.apply((x, y) => new ClassTypeAnn(x, y)).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "handle constructor references with type annotations with a lossy wrapper lambda" in {
      val fn = Functoid.apply((x, y) => new ClassTypeAnnByName(x, y)).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "handle case class .apply references with type annotations" in {
      val fn = Functoid.apply(ClassTypeAnn.apply _).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "handle case class .apply references with javax.inject.Named annotations" in {
      val fn = Functoid.apply(ClassTypeAnnJavax.apply _).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "handle generic case class .apply references with type annotations" in {
      val fn = Functoid.apply(ClassTypeAnnT.apply[String, Int] _).get

      assert(fn.diKeys contains DIKey.get[String].named("classtypeann1"))
      assert(fn.diKeys contains DIKey.get[Int].named("classtypeann2"))
    }

    "handle generic parameters with Tag" in {
      def fn[T: Tag] = Functoid.apply((x: T @Id("gentypeann")) => x).get

      assert(fn[Int].diKeys contains DIKey.get[Int].named("gentypeann"))
      assert(fn[String].diKeys contains DIKey.get[String].named("gentypeann"))
    }

    "handle higher-kinded parameters with TagK" in {
      def fn[F[_]: TagK] = Functoid.apply((x: F[Int] @Id("gentypeann")) => x).get

      assert(fn[List].diKeys contains DIKey.get[List[Int]].named("gentypeann"))
      assert(fn[Set].diKeys contains DIKey.get[Set[Int]].named("gentypeann"))
    }

    "handle by-name val calls" in {
      val fn = Functoid.apply(testValByName).get

      assert(fn.diKeys contains DIKey.get[Any])
      var counter = 0
      class CountInstantiations { counter += 1 }
      fn.unsafeApply(Seq(TypedRef.byName(new CountInstantiations)))
      assert(counter == 0)
    }

    "zip is correct" in {
      val fn = Functoid.pure(5).zip(Functoid.pure("Hello"))

      assert(fn.get.parameters.isEmpty)
      assert(fn.get.ret == SafeType.get[(Int, String)])
    }

    "map2 is correct" in {
      val fn = Functoid
        .pure(5).map2(Functoid.identity[String])((i: Int, s: String) => StringContext(s + i))

      assert(fn.get.unsafeApply(Seq(TypedRef("Hello"))) == StringContext("Hello5"))
      assert(fn.get.parameters.size == 1)
      assert(fn.get.parameters.head.key == DIKey.get[String])
      assert(fn.get.ret == SafeType.get[StringContext])
    }

    "flatAp is correct" in {
      val fn = Functoid
        .pure(5).flatAp((s: String) => (i: Int) => StringContext(s + i))

      assert(fn.get.unsafeApply(Seq(TypedRef("Hello"))) == StringContext("Hello5"))
      assert(fn.get.parameters.size == 1)
      assert(fn.get.parameters.head.key == DIKey.get[String])
      assert(fn.get.ret == SafeType.get[StringContext])
    }

    "ap is correct" in {
      val fn = Functoid((s: String) => (i: Int) => StringContext(s + i)).ap(Functoid.pure(5))

      assert(fn.get.unsafeApply(Seq(TypedRef("Hello"))) == StringContext("Hello5"))
      assert(fn.get.parameters.size == 1)
      assert(fn.get.parameters.head.key == DIKey.get[String])
      assert(fn.get.ret == SafeType.get[StringContext])
    }

    "Functoid.single is correct" in {
      val fn = Functoid.single((_: String).length)

      assert(fn.get.unsafeApply(Seq(TypedRef("Hello"))) == 5)
      assert(fn.get.parameters.size == 1)
      assert(fn.get.parameters.head.key == DIKey.get[String])
      assert(fn.get.ret == SafeType.get[Int])
    }

    "Functoid.singleton is correct" in {
      val xa = new {}
      val fn = Functoid.singleton[xa.type](xa)

      assert(fn.get.unsafeApply(Seq()).asInstanceOf[AnyRef] eq xa)
      assert(fn.get.parameters.isEmpty)
      assert(fn.get.ret == SafeType.get[xa.type])
    }

    "Functoid.singleton is correct with constant types" in {
      import Ordering.Implicits._
      assume(ScalaRelease.parse(MacroParameters.scalaVersion().get) >= ScalaRelease.`2_13`(0))
      assertCompiles(
        """
      val fn = Functoid.singleton["xa"]("xa")

      assert(fn.get.unsafeApply(Seq()) == "xa")
      assert(fn.get.parameters.isEmpty)
      assert(fn.get.ret == SafeType.get["xa"])
        """
      )
    }

    "generic parameters without Tag should fail" in {
      assertTypeError(
        """def fn[T] = Functoid.apply((x: T @Id("gentypeann")) => x).get"""
      )
    }

    "should be equal for the same function value" in {
      val fn: Int => String = (i: Int) => i.toString

      val p1: Functoid[String] = fn
      val p2: Functoid[String] = fn

      assert(p1 == p2)
    }

    "should be unequal after .map" in {
      val p1 = ClassConstructor[Some[Int]]
      val p2 = p1.map(identity)
      val p3 = p2.map(identity)

      assert(p1 != p2)
      assert(p2 != p3)
    }

    "should be unequal after .addUnused" in {
      val p1 = ClassConstructor[Some[Int]]
      val p2 = p1.addDependency[Int]
      val p3 = p2.addDependency[String]
      val p4 = p3.addDependency[Unit]

      assert(p1 != p2)
      assert(p2 != p3)
      assert(p3 != p4)
    }

    "fail on multiple conflicting annotations on the same parameter" in {
      assertTypeError("Functoid.apply(defconfannfn _)")
      assertTypeError("Functoid.apply(defconfannfn2 _)")
    }

    "extract Id annotations from higher-kinded type aliases" in {
      import BasicCase7._

      def ctor[F[_]](componentSpecial: ComponentSpecial[F]): Component[F] = componentSpecial

      def fn[F[_]: TagK]: Functoid[ComponentSpecial[F]] = Functoid.apply(ctor[F] _)

      assert(fn[Identity].get.diKeys == Seq(DIKey[Component[Identity]]("special")))
    }

    "extract Id annotations of parameterized ClassConstructor" in {
      val fn = ClassConstructor[Component1[Option]]

      assert(fn.get.diKeys == Seq(DIKey[Unit]("needed")))
    }

    "can handle case class .apply references with argument annotations" in {
      val fn = Functoid.apply(ClassArgAnn.apply _).get

      assert(fn.diKeys.contains(DIKey.get[String].named("classargann1")))
      assert(fn.diKeys.contains(DIKey.get[Int].named("classargann2")))
    }

    "progression test: Can't expand functions with implicit arguments" in {
      intercept[TestFailedException] {
        assertCompiles("Functoid.apply(defimplicitfn _)")
      }
    }
  }

}
