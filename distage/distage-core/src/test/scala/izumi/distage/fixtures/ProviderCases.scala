package izumi.distage.fixtures

import izumi.distage.model.definition.Id
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.language.unused

@ExposedTestScope
object ProviderCases {

  object ProviderCase1 {
    def deftypeannfn(y: String @Id("deftypeann"), z: Int @Id("deftypeann2")): String = Function.const(y)(z)
    def deftypeannfnbyname(y: => String @Id("deftypeann"), z: => Int @Id("deftypeann2")): String = Function.const(y)(z)

    def defargannfn(@Id("defargann") y: String, @Id("defargann2") z: Int): String = Function.const(y)(z)

    def defconfannfn(@Id("confargann") y: String @Id("conftypeann")): String = y

    def defconfannfn2(@Id("confargann1") @Id("confargann2") y: String): String = y

    def defimplicitfn(implicit y: String): String = y

    val testVal: (String @Id("valsigtypeann1"), Int @Id("valsigtypeann2")) => String = (x, _) => x

    val testVal2: Boolean => String = { x: Boolean @Id("valbodytypeann") => x.toString }

    val testVal3: Long @Id("valsbtypeann1") => String @Id("valsbtypeann2") => Long =
      { x: Long @Id("valsbtypeann3") => _ => x }

    val testValByName: (=> Any) => Unit = _ => ()

    case class ClassArgAnn(@Id("classargann1") x: String, @Id("classargann2") y: Int)
    case class ClassTypeAnn(x: String @Id("classtypeann1"), y: Int @Id("classtypeann2"))
    class ClassTypeAnnByName(@unused x: => String @Id("classtypeann1"), @unused y: => Int @Id("classtypeann2"))

    class Poly[F[_]]

    def poly[F[_]](f: F[Int]): F[Unit] => Poly[F] = _ => { f.discard() ; new Poly[F] }

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
}
