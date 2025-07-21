package izumi.distage

import distage.{ModuleBase, ModuleDef}
import izumi.distage.fixtures.BasicCases._
import org.scalatest.wordspec.AnyWordSpec

class StaticDSLTest extends AnyWordSpec {

  "Static DSL" should {

    "allow to define static contexts" in {
      import BasicCase1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass].fromClass[TestClass]
        make[TestDependency0].fromClass[TestImpl0]
        make[TestInstanceBinding].from(TestInstanceBinding())

        make[TestClass]
          .named("named.test.class")
          .fromClass[TestClass]
        make[TestDependency0]
          .named("named.test.dependency.0")
          .fromTrait[TestDependency0]
        make[TestInstanceBinding]
          .named("named.test")
          .from(TestInstanceBinding())
        many[JustTrait]
          .named("named.empty.set")
        many[JustTrait]
          .addClass[Impl0]
          .add(new Impl1)
          .addTrait[JustTrait]
        many[JustTrait]
          .named("named.set")
          .add(new Impl2())
        many[JustTrait]
          .named("named.set")
          .addClass[Impl3]
      }

      assert(definition != null)
    }
  }

}
