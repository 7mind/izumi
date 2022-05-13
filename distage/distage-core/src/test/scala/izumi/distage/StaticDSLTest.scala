package izumi.distage

import distage.{ModuleBase, ModuleDef}
import izumi.distage.fixtures.BasicCases._
import org.scalatest.wordspec.AnyWordSpec

class StaticDSLTest extends AnyWordSpec {

  "Static DSL" should {

    "allow to define static contexts" in {
      import BasicCase1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass].from[TestClass]
        make[TestDependency0].from[TestImpl0]
        make[TestInstanceBinding].from(TestInstanceBinding())

        make[TestClass]
          .named("named.test.class")
          .from[TestClass]
        make[TestDependency0]
          .named("named.test.dependency.0")
          .from[TestDependency0]
        make[TestInstanceBinding]
          .named("named.test")
          .from(TestInstanceBinding())
        many[JustTrait]
          .named("named.empty.set")
        many[JustTrait]
          .add[Impl0]
          .add(new Impl1)
          .add[JustTrait]
        many[JustTrait]
          .named("named.set")
          .add(new Impl2)
        many[JustTrait]
          .named("named.set")
          .add[Impl3]
      }

      assert(definition != null)
    }
  }

}
