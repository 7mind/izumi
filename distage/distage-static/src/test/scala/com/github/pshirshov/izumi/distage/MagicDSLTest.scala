package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.definition.Bindings._
import com.github.pshirshov.izumi.distage.model.definition.MagicDSL._
import com.github.pshirshov.izumi.distage.model.definition.{Bindings, ModuleDef, ModuleBase, SimpleModuleDef}
import org.scalatest.WordSpec

class MagicDSLTest extends WordSpec {

  "Magic DSL" should {

    "allow to define magic contexts" in {
      import Case1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass].magically
        make[TestDependency0].fromMagic[TestImpl0]
        make[TestInstanceBinding].from(TestInstanceBinding())

        make[TestClass].named("named.test.class")
          .magically
        make[TestDependency0].named("named.test.dependency.0")
          .magically
        make[TestInstanceBinding].named("named.test")
          .from(TestInstanceBinding())
        many[JustTrait]
          .named("named.empty.set")
        many[JustTrait]
          .add[Impl0]
          .add(new Impl1)
          .addMagic[JustTrait]
        many[JustTrait].named("named.set")
          .add(new Impl2())
        many[JustTrait].named("named.set")
          .add[Impl3]
      }

      assert(definition != null)
    }
  }

}
