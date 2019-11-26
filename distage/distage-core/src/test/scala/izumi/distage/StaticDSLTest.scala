package izumi.distage

import distage.{ModuleBase, ModuleDef}
import izumi.distage.constructors.StaticDSL._
import izumi.distage.fixtures.BasicCases._
import org.scalatest.WordSpec

class StaticDSLTest extends WordSpec {

  "Static DSL" should {

    "allow to define static contexts" in {
      import BasicCase1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass].stat[TestClass]
        make[TestDependency0].stat[TestImpl0]
        make[TestInstanceBinding].from(TestInstanceBinding())

        make[TestClass]
          .named("named.test.class")
          .stat[TestClass]
        make[TestDependency0]
          .named("named.test.dependency.0")
          .stat[TestDependency0]
        make[TestInstanceBinding]
          .named("named.test")
          .from(TestInstanceBinding())
        many[JustTrait]
          .named("named.empty.set")
        many[JustTrait]
          .addStatic[Impl0]
          .add(new Impl1)
          .addStatic[JustTrait]
        many[JustTrait]
          .named("named.set")
          .add(new Impl2())
        many[JustTrait]
          .named("named.set")
          .addStatic[Impl3]
      }

      assert(definition != null)
    }
  }

}
