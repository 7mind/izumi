package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.definition.{Id, With}
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

import scala.language.higherKinds
import scala.util.Random

@ExposedTestScope
object Fixtures {

  object BasicCase1 {

    trait TestDependency0 {
      def boom(): Int = 1
    }

    class TestImpl0 extends TestDependency0 {

    }

    trait NotInContext {}

    trait TestDependency1 {
      def unresolved: NotInContext
    }

    trait TestDependency3 {
      def methodDependency: TestDependency0

      def doSomeMagic(): Int = methodDependency.boom()
    }

    class TestClass
    (
      val fieldArgDependency: TestDependency0
      , argDependency: TestDependency1
    ) {
      val x = argDependency
      val y = fieldArgDependency
    }

    final case class TestCaseClass(a1: TestClass, a2: TestDependency3)

    final case class TestInstanceBinding(z: String =
                                   """R-r-rollin' down the window, white widow, fuck fame
Forest fire, climbin' higher, real life, it can wait""")

    final case class TestCaseClass2(a: TestInstanceBinding)

    trait JustTrait {}

    class Impl0 extends JustTrait

    class Impl1 extends JustTrait

    class Impl2 extends JustTrait

    class Impl3 extends JustTrait

  }

  object BasicCase2 {

    trait TestDependency0 {
      def boom(): Int
    }

    class TestImpl0Good extends TestDependency0 {
      override def boom(): Int = 1
    }

    class TestImpl0Bad extends TestDependency0 {
      override def boom(): Int = 9
    }

    class TestClass
    (
      @Id("named.test.dependency.0") val fieldArgDependency: TestDependency0
      , @Id("named.test") argDependency: TestInstanceBinding
    ) {
      val x = argDependency
      val y = fieldArgDependency

      def correctWired(): Boolean = {
        fieldArgDependency.boom() == 1
      }
    }

    final case class TestInstanceBinding(z: String =
                                   """R-r-rollin' down the window, white widow, fuck fame
Forest fire, climbin' higher, real life, it can wait""")

  }

  object BasicCase3 {

    trait Dependency

    class Impl1 extends Dependency

    class Impl2 extends Dependency

  }

  object CircularCase1 {

    trait Circular1 {
      def arg: Circular2
    }

    class Circular2(val arg: Circular1)
  }

  object CircularCase2 {

    trait Circular1 {
      def arg: Circular2
    }

    trait Circular2 {
      def arg: Circular3
    }


    trait Circular3 {
      def arg: Circular4

      def arg2: Circular5

      def method: Long = 2L
    }

    trait Circular4 {
      def arg: Circular1

      def factoryFun(c4: Circular4, c5: Circular5): Circular3

      def testVal: Int = 1
    }

    trait Circular5 {
      def arg: Circular1

      def arg2: Circular4
    }

    trait CircularBad1 {
      def arg: CircularBad2

      def bad() = {
        if (Random.nextInt(10) < 100) {
          throw new RuntimeException()
        }
      }

      bad()
    }

    trait CircularBad2 {
      def arg: CircularBad1

      def bad() = {
        if (Random.nextInt(10) < 100) {
          throw new RuntimeException()
        }
      }

      bad()
    }

  }

  object CircularCase3 {
    class SelfReference(val self: SelfReference)

    class ByNameSelfReference(_self: => ByNameSelfReference) {
      final lazy val self = _self
    }
  }

  object FactoryCase1 {

    trait Dependency {
      def isSpecial: Boolean = false

      def isVerySpecial: Boolean = false

      override def toString: String = s"Dependency($isSpecial, $isVerySpecial)"
    }

    final case class ConcreteDep() extends Dependency

    final case class SpecialDep() extends Dependency {
      override def isSpecial: Boolean = true
    }

    final case class VerySpecialDep() extends Dependency {
      override def isVerySpecial: Boolean = true
    }

    final case class TestClass(b: Dependency)

    final case class AssistedTestClass(b: Dependency, a: Int)

    final case class NamedAssistedTestClass(@Id("special") b: Dependency, a: Int)

    final case class GenericAssistedTestClass[T, S](a: List[T], b: List[S], c: Dependency)

    trait Factory {
      def wiringTargetForDependency: Dependency

      def factoryMethodForDependency(): Dependency

      def x(): TestClass
    }

    trait OverridingFactory {
      def x(b: Dependency): TestClass
    }

    trait AssistedFactory {
      def x(a: Int): AssistedTestClass
    }

    trait NamedAssistedFactory {
      def dep: Dependency @Id("veryspecial")

      def x(a: Int): NamedAssistedTestClass
    }

    trait GenericAssistedFactory {
      def x[T, S](t: List[T], s: List[S]): GenericAssistedTestClass[T, S]
    }

    trait AbstractDependency

    case class AbstractDependencyImpl(a: Dependency) extends AbstractDependency

    trait FullyAbstractDependency {
      def a: Dependency
    }

    trait AbstractFactory {
      @With[AbstractDependencyImpl]
      def x(): AbstractDependency

      def y(): FullyAbstractDependency
    }

    trait FactoryProducingFactory {
      def x(): Factory
    }

  }

  object TraitCase1 {

    class Dependency1

    trait TestTrait {
      def dep: Dependency1
    }

  }

  object TraitCase2 {

    class Dependency1 {
      override def toString: String = "Hello World"
    }

    class Dependency2

    class Dependency3

    trait Trait1 {
      protected def dep1: Dependency1
    }

    trait Trait2 extends Trait1 {
      override protected def dep1: Dependency1

      def dep2: Dependency2
    }

    trait Trait3 extends Trait1 with Trait2 {
      def dep3: Dependency3

      def prr(): String = dep1.toString
    }

  }

  object TraitCase3 {

    trait ATraitWithAField {
      def method: Int = 1

      val field: Int = 1
    }

  }

  object TraitCase4 {

    trait Dep {
      def isA: Boolean
    }

    class DepA extends Dep {
      override def isA: Boolean = true
    }

    class DepB extends Dep {
      override def isA: Boolean = false
    }

    trait Trait {
      @Id("A")
      def depA: Dep

      @Id("B")
      def depB: Dep
    }

    trait Trait1 {
      def depA: Dep @Id("A")

      def depB: Dep @Id("B")
    }

  }

  object TraitCase5 {

    final case class Dep()

    trait TestTrait {
      protected val dep: Dep
      def rd: String = {
        dep.toString
      }
    }
  }

  object TypesCase1 {

    trait Dep

    final case class DepA() extends Dep

    final case class DepB() extends Dep

    type TypeAliasDepA = DepA

    final case class TestClass[D](inner: List[D])

    final case class TestClass2[D](inner: D)

    trait TestTrait {
      def dep: TypeAliasDepA
    }

  }

  object TypesCase2 {

    class Dep()

    final case class Parameterized[T](t: T)

    trait ParameterizedTrait[T] {
      def t: T
    }

  }

  object ProviderCase1 {
    def deftypeannfn(y: String @Id("deftypeann"), z: Int @Id("deftypeann2")): String = Function.const(y)(z)

    def defargannfn(@Id("defargann") y: String, @Id("defargann2") z: Int): String = Function.const(y)(z)

    def defconfannfn(@Id("confargann") y: String @Id("conftypeann")): String = y

    def defconfannfn2(@Id("confargann1") @Id("confargann2") y: String): String = y

    val testVal: (String @Id("valsigtypeann1"), Int @Id("valsigtypeann2")) => String = (x, _) => x

    val testVal2: Boolean => String = { x: Boolean @Id("valbodytypeann") => x.toString }

    val testVal3: Long @Id("valsbtypeann1") => String @Id("valsbtypeann2") => Long =
      { x: Long @Id("valsbtypeann3") => _ => x }

    case class ClassArgAnn(@Id("classargann1") x: String, @Id("classargann2") y: Int)
    case class ClassTypeAnn(x: String @Id("classtypeann1"), y: Int @Id("classtypeann2"))

    class TestProviderModule {

      class TestDependency

      class TestClass(val a: TestDependency)

      def implArg(@Id("classdefargann1") arganndep: TestDependency): TestClass = new TestClass(arganndep)

      def implType(typeanndep: TestDependency @Id("classdeftypeann1")): TestClass = new TestClass(typeanndep)

    }

  }

  object ProviderCase2 {

    trait Dependency1

    trait Dependency1Sub extends Dependency1

    class TestClass(val b: Dependency1)

    class TestClass2(val a: TestClass)

  }

  object ProviderCase3 {

    class TestDependency

    class TestClass(val a: TestDependency)

    def implArg(@Id("classdefargann1") arganndep: TestDependency): TestClass = new TestClass(arganndep)

    def implType(typeanndep: TestDependency @Id("classdeftypeann1")): TestClass = new TestClass(typeanndep)

  }

  object SetCase1 {

    trait SetTrait
    class SetImpl1 extends SetTrait
    class SetImpl2 extends SetTrait
    class SetImpl3 extends SetTrait

    case class Service0(set: Set[SetTrait])

    case class Service1(@Id("n1") set: Set[SetTrait])

    case class Service2(service3: Service3, @Id("n2") set: Set[SetTrait])

    case class Service3(@Id("n3") set: Set[SetTrait])

  }

  object SetCase2 {
    trait Service
    class Service1 extends Service
  }

  object HigherKindsCase1 {
    type id[A] = A

    trait Pointed[F[_]] {
      def point[A](a: A): F[A]
    }

    object Pointed {
      def apply[F[_]: Pointed]: Pointed[F] = implicitly[Pointed[F]]

      implicit final val pointedList: Pointed[List] =
        new Pointed[List] {
          override def point[A](a: A): List[A] = List(a)
        }

      implicit final def pointedOptionT[F[_]: Pointed]: Pointed[OptionT[F, ?]] =
        new Pointed[OptionT[F, ?]] {
          override def point[A](a: A): OptionT[F, A] = OptionT(Pointed[F].point(Some(a)))
        }

      implicit final val pointedId: Pointed[id] =
        new Pointed[id] {
          override def point[A](a: A): id[A] = a
        }

    }

    case class OptionT[F[_], A](value: F[Option[A]])

    trait TestTrait {
      type R[_]

      def get: R[Int]
    }

    // TODO: @Id(this)
    class TestServiceClass[F[_]: Pointed](@Id("TestService") getResult: Int) extends TestTrait {
      override type R[_] = F[_]

      override def get: F[Int] = {
        Pointed[F].point(getResult)
      }
    }

    trait TestServiceTrait[F[_]] extends TestTrait {
      override type R[_] = F[_]

      implicit protected val pointed: Pointed[F]

      protected val getResult: Int @Id("TestService")

      override def get: F[_] = Pointed[F].point(getResult * 2)
    }

    abstract class TestProvider[A, F[_]: Pointed] {
      def f(a: A): F[A]
    }

    abstract class TestProvider0[A, B, F[_]: Pointed] {
      def f(a: A): F[A]
    }

    abstract class TestProvider1[A, G[_]: Pointed, F[_]: Pointed] {
      def f(a: A): F[A]
    }
  }

  object ImplicitCase1 {

    class MyDummyImplicit extends DummyImplicit {
      def imADummy: Boolean = true
    }

    class Dep

    final case class TestClass(dep: Dep)(implicit val dummyImplicit: DummyImplicit)

  }

  object ImplicitCase2 {
    class TestDependency1
    class TestDependency2
    class TestDependency3

    class TestClass(val a: TestDependency1)(val b: TestDependency3, implicit val c: TestDependency2)(implicit val d: TestDependency3)
  }

}
