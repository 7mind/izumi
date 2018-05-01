package com.github.pshirshov.izumi.distage

import cats.implicits._
import com.github.pshirshov.izumi.distage.DIStageInstances._
import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.definition.Bindings.binding
import com.github.pshirshov.izumi.distage.model.definition._
import org.scalatest.WordSpec

class DIStageInstancesTest extends WordSpec {
  "cats instances for ContextDefinition" should {
    "allow monoidal operations between different types of binding dsls" in {
      import Case1._

      val mod1: ModuleDef = new ModuleBuilder {
        bind[TestClass]
      }

      val mod2: ModuleDef = TrivialModuleDef
        .bind[TestCaseClass2]

      val mod3_1: ModuleDef = TrivialModuleDef
        .bind[TestDependency1]

      val mod3_2 = TrivialModuleDef

      val mod3 = (mod3_1 |+| mod3_2).bind[NotInContext]

      val mod4 = TrivialModuleDef(Set(
        binding(TestInstanceBinding())
      ))

      val moduleDef: ModuleDef = TrivialModuleDef
      val mod5: BindingDSL = moduleDef ++ Bindings.binding[TestDependency0, TestImpl0]

      val combinedModules = Vector[ModuleDef](mod1, mod2, mod3, mod4, mod5).combineAll

      val plusModules = mod5 |+| mod4 |+| mod3 |+| mod2 |+| mod1

      val complexModule = TrivialModuleDef
        .bind[TestClass]
        .bind[TestDependency0].as[TestImpl0]
        .bind[TestCaseClass2]
        .bind(TestInstanceBinding())
        .++(mod3) // function pointer equality on magic trait providers

      assert(combinedModules === complexModule)
      assert(plusModules === complexModule)
    }
  }
}
