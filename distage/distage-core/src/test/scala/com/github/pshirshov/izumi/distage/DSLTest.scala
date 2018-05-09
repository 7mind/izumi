package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures.Case1.NotInContext
import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.definition.{Bindings, ModuleBase, ModuleDef, SimpleModuleDef}
import org.scalatest.WordSpec

class DSLTest extends WordSpec {

  "Basic DSL" should {
    "allow to define contexts" in {
      import Case1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass]
        make[TestDependency0].from[TestImpl0]
        make[TestInstanceBinding].from(TestInstanceBinding())

        make[TestClass]
          .named("named.test.class")
        make[TestDependency0]
          .named("named.test.dependency.0")
        make[TestInstanceBinding].named("named.test")
          .from(TestInstanceBinding())
        many[JustTrait].named("named.empty.set")
        many[JustTrait]
          .add[Impl0]
          .add(new Impl1)
        many[JustTrait].named("named.set")
          .add(new Impl2())
        many[JustTrait].named("named.set")
          .add[Impl3]
      }

      assert(definition != null)
    }
  }

  "Module DSL" should {
    "allow to define contexts" in {
      import Case1._

      object Module extends ModuleDef {
        make[TestClass]
        make[TestDependency0].from[TestImpl0]
      }

      assert(Module.bindings == Set(
        Bindings.binding[TestClass]
        , Bindings.binding[TestDependency0, TestImpl0]
      ))
    }

    "allow monoidal operations between different types of binding dsls" in {
      import Case1._

      val mod1: ModuleBase = new ModuleDef {
        make[TestClass]
      }

      val mod2: ModuleBase = new ModuleDef {
        make[TestCaseClass2]
      }

      val mod3_1 = new ModuleDef {
        make[TestDependency1]
      }

      val mod3_2 = SimpleModuleDef.empty

      val mod3 = (mod3_1 ++ mod3_2) :+ Bindings.binding[NotInContext]

      val mod4: ModuleBase = SimpleModuleDef {
        Set(
          Bindings.binding(TestInstanceBinding())
        )
      }

      val mod5: ModuleBase = (SimpleModuleDef.empty
        :+ Bindings.binding[TestDependency0, TestImpl0]
        )

      val combinedModules = Seq(mod1, mod2, mod3, mod4, mod5)
        .foldLeft[ModuleBase](SimpleModuleDef.empty)(_ ++ _)

      val complexModule = SimpleModuleDef(Set(
        Bindings.binding[TestClass]
        , Bindings.binding[TestDependency0, TestImpl0]
        , Bindings.binding[TestCaseClass2]
        , Bindings.binding(TestInstanceBinding())
      ))
        .++(mod3) // function pointer equality on magic trait providers

      assert(combinedModules == complexModule)
    }
  }

}
