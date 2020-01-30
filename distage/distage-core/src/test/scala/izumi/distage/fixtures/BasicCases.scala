package izumi.distage.fixtures

import distage.{LocatorRef, ModuleDef}
import izumi.distage.model.definition.Id
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.fundamentals.platform.language.Quirks

@ExposedTestScope
object BasicCases {

  object BasicCase1 {

    trait TestDependency0 {
      def boom(): Int = 1
    }

    class TestImpl0 extends TestDependency0

    trait NotInContext

    trait TestDependency1 {
      def unresolved: NotInContext
    }

    trait TestDependency3 {
      def methodDependency: TestDependency0

      def doSomeMagic(): Int = methodDependency.boom()
    }

    class TestClass
    (
      val fieldArgDependency: TestDependency0,
      argDependency: TestDependency1,
    ) {
      val x = argDependency
      val y = fieldArgDependency
    }

    case class TestCaseClass(a1: TestClass, a2: TestDependency3)

    case class TestInstanceBinding(z: String = "TestValue")

    case class TestCaseClass2(a: TestInstanceBinding)

    trait JustTrait

    class Impl0 extends JustTrait

    class Impl1 extends JustTrait

    class Impl2 extends JustTrait

    class Impl3 extends JustTrait

    case class LocatorDependent(ref: LocatorRef)

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
      , @Id("izumi.distage.fixtures.basiccases.basiccase2.testdependency0") val fieldArgDependencyAutoname: TestDependency0
      , @Id("named.test") argDependency: => TestInstanceBinding
    ) {
      val x = argDependency
      val y = fieldArgDependency

      def correctWired(): Boolean = {
        argDependency.z.nonEmpty && fieldArgDependency.boom() == 1 && fieldArgDependencyAutoname.boom() == 1 && (fieldArgDependency ne fieldArgDependencyAutoname)
      }
    }

    case class TestInstanceBinding(z: String =
                                         """R-r-rollin' down the window, white widow, fuck fame
Forest fire, climbin' higher, real life, it can wait""")

  }

  object BasicCase3 {

    trait Dependency

    class Impl1 extends Dependency

    class Impl2 extends Dependency

  }

  object BasicCaseIssue762 {
    class MyClass(val a: (scala.Predef.String {}) @Id("a"), val b: String @Id("b"))

    object MyClassModule extends ModuleDef {
      make[MyClass]
    }

    object ConfigModule extends ModuleDef {
      make[scala.Predef.String].named("a").from("applicationId")
      make[Predef.String].named("b").from { a: String @Id("a") => a }
    }
  }

  object BadAnnotationsCase {
    def value = "xxx"
    trait TestDependency0

    class TestClass
    (
      @Id(value) val fieldArgDependency: TestDependency0
    )
  }

  object BasicCase4 {
    trait Dependency

    class TestClass(tyAnnDependency: Dependency @Id("special")) {
      Quirks.discard(tyAnnDependency)
    }

    case class ClassTypeAnnT[A, B](val x: A @Id("classtypeann1"), y: B @Id("classtypeann2"))
  }

  object BasicCase5 {
    trait TestDependency

    class TestImpl1(val justASet: Set[TestDependency])
  }

  object BasicCase6 {
    trait TraitX {
      def x(): String
    }

    trait TraitY {
      def y(): String
    }

    trait TraitZ {
      def z(): String
    }

    class ImplXYZ extends TraitX with TraitY with TraitZ {
      override def x(): String = "X"
      override def y(): String = "Y"
      override def z(): String = "Z"
    }

  }

  object BasicCase7 {
    type Port = Int @Id("port")
    type Address = String @Id("address")

    final case class ServerConfig(port: Port, address: Address)
  }

  object BasicCase8 {
    class Beep[A]
    class Bop[A : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep]
    (implicit val beep: Beep[A])

    trait BopTrait[A] {
      def beep0: Beep[A]
      def beep1: Beep[A]
      def beep2: Beep[A]
      def beep3: Beep[A]
      def beep4: Beep[A]
      def beep5: Beep[A]
      def beep6: Beep[A]
      def beep7: Beep[A]
      def beep8: Beep[A]
      def beep9: Beep[A]
      def beep10: Beep[A]
      def beep11: Beep[A]
      def beep12: Beep[A]
      def beep13: Beep[A]
      def beep14: Beep[A]
      def beep15: Beep[A]
      def beep16: Beep[A]
      def beep17: Beep[A]
      def beep18: Beep[A]
      def beep19: Beep[A]
      def beep20: Beep[A]
      def beep21: Beep[A]
      def beep22: Beep[A]
      def beep23: Beep[A]
      def beep24: Beep[A]
      def beep25: Beep[A]
      def beep26: Beep[A]
      def beep27: Beep[A]
      def beep28: Beep[A]
      def beep29: Beep[A]
    }

    abstract class BopAbstractClass[A : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep
    : Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep: Beep]
    (implicit val beep: Beep[A]) extends BopTrait[A]

    case class BeepDependency[A]()(implicit val beep: Beep[A])
    case class BeepDependency1[A](i: Int)(implicit val beep: Beep[A])

    trait BopFactory[A] extends BopTrait[A] {
      def x(): BeepDependency[A]
      def x(i: Int): BeepDependency1[A]
    }
  }

}
