package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.definition.Bindings._
import com.github.pshirshov.izumi.distage.model.definition.MagicDSL._
import com.github.pshirshov.izumi.distage.model.definition.{Bindings, ModuleBuilder, ModuleDef, TrivialModuleDef}
import org.scalatest.WordSpec

class MagicDSLTest extends WordSpec {

  "Magic DSL" should {

    "allow to define magic contexts" in {
      import Case1._
      val definition: ModuleDef = TrivialModuleDef
        .magic[TestClass]
        .bind[TestDependency0].magically[TestImpl0]
        .bind(TestInstanceBinding())

        .magic[TestClass]
          .named("named.test.class")
        .magic[TestDependency0]
          .named("named.test.dependency.0")
        .bind(TestInstanceBinding())
          .named("named.test")
        .set[JustTrait]
          .named("named.empty.set")
        .set[JustTrait]
          .element[Impl0]
          .element(new Impl1)
        .set[JustTrait]
          .element(new Impl2())
            .named("named.set")
        .set[JustTrait]
          .element[Impl3]
            .named("named.set")

      assert(definition != null)
    }
  }

  "Module DSL" should {
    "allow to define contexts" in {
      import Case1._

      object Module extends ModuleBuilder {
        bind[TestClass]
        bind[TestDependency0].as[TestImpl0]
      }

      assert(Module.bindings == Set(
        Bindings.binding[TestClass]
        , Bindings.binding[TestDependency0, TestImpl0]
      ))
    }

    "allow monoidal operations between different types of binding dsls" in {
      import Case1._

      val mod1: ModuleDef = new ModuleBuilder {
        bind[TestClass]
      }

      val mod2: ModuleDef = TrivialModuleDef
        .bind[TestCaseClass2]

      val mod3_1 = TrivialModuleDef
        .magic[TestDependency1]

      val mod3_2 = TrivialModuleDef

      val mod3 = (mod3_1 ++ mod3_2).magic[NotInContext]

      val mod4: ModuleDef = TrivialModuleDef(Set(
        binding(TestInstanceBinding())
      ))

      val mod5: ModuleDef = (TrivialModuleDef
        ++ Bindings.binding[TestDependency0, TestImpl0]
        )

      val combinedModules = Seq(mod1, mod2, mod3, mod4, mod5)
        .foldLeft(TrivialModuleDef: ModuleDef)(_ ++ _)

      val complexModule = TrivialModuleDef
        .bind[TestClass]
        .bind[TestDependency0].as[TestImpl0]
        .bind[TestCaseClass2]
        .bind(TestInstanceBinding())
        .++(mod3) // function pointer equality on magic trait providers

      assert(combinedModules == complexModule)
    }
  }

}
