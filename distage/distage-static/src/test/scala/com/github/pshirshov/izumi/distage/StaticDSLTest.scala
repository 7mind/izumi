package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.definition.StaticDSL._
import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import org.scalatest.WordSpec

class StaticDSLTest extends WordSpec {

  "Static DSL" should {

    "allow to define static contexts" in {
      import Case1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass].statically
        make[TestDependency0].static[TestImpl0]
        make[TestInstanceBinding].from(TestInstanceBinding())

        make[TestClass].named("named.test.class")
          .statically
        make[TestDependency0].named("named.test.dependency.0")
          .statically
        make[TestInstanceBinding].named("named.test")
          .from(TestInstanceBinding())
        many[JustTrait]
          .named("named.empty.set")
        many[JustTrait]
          .addStatic[Impl0]
          .add(new Impl1)
          .addStatic[JustTrait]
        many[JustTrait].named("named.set")
          .add(new Impl2())
        many[JustTrait].named("named.set")
          .addStatic[Impl3]
      }

      assert(definition != null)
    }
  }

}
