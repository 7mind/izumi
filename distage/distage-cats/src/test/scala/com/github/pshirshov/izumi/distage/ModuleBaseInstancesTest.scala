package com.github.pshirshov.izumi.distage

import cats.implicits._
import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.definition.Bindings.binding
import com.github.pshirshov.izumi.distage.model.definition._
import distage.interop.cats._
import org.scalatest.WordSpec

class ModuleBaseInstancesTest extends WordSpec {
  "cats instances for ContextDefinition" should {
    "allow monoidal operations between different types of binding dsls" in {
      import Case1._

      val mod1: ModuleBase = new ModuleDef {
        make[TestClass]
      }

      val mod2: ModuleBase = new ModuleDef {
        make[TestCaseClass2]
      }

      val mod3_1: ModuleBase = new ModuleDef {
        make[TestDependency1]
      }

      val mod3_2 = SimpleModuleDef.empty

      val mod3 = (mod3_1 |+| mod3_2) :+ binding[NotInContext]

      val mod4 = SimpleModuleDef(Set(
        binding(TestInstanceBinding())
      ))

      val moduleDef: ModuleBase = SimpleModuleDef.empty
      val mod5 = moduleDef :+ Bindings.binding[TestDependency0, TestImpl0]

      val combinedModules = Vector[ModuleBase](mod1, mod2, mod3, mod4, mod5).combineAll

      val plusModules = mod5 |+| mod4 |+| mod3 |+| mod2 |+| mod1

      val complexModule = SimpleModuleDef(Set(
        Bindings.binding[TestClass]
        , Bindings.binding[TestDependency0, TestImpl0]
        , Bindings.binding[TestCaseClass2]
        , Bindings.binding(TestInstanceBinding())
      )) ++ mod3 // function pointer equality on magic trait providers

      assert(combinedModules === complexModule)
      assert(plusModules === complexModule)
    }
  }
}
