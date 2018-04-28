package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.definition.Bindings.binding
import com.github.pshirshov.izumi.distage.model.definition.{Bindings, ContextDefinition, ModuleBuilder, TrivialDIDef}
import com.github.pshirshov.izumi.distage.definition.MagicDSL._
import org.scalatest.WordSpec
import DIStageInstances._
import cats.implicits._

class ContextDefinitionTest extends WordSpec {
  "cats instances for ContextDefinition" should {
    "allow monoidal operations between different types of binding dsls" in {
      import Case1._

      val mod1: ContextDefinition = new ModuleBuilder {
        bind[TestClass]
      }

      val mod2: ContextDefinition = TrivialDIDef
        .bind[TestCaseClass2]

      val mod3: ContextDefinition = TrivialDIDef
        .magic[TestDependency1]
        .magic[NotInContext]

      val mod4: ContextDefinition = Set(
        binding(TestInstanceBinding())
      )

      val mod5: ContextDefinition = (TrivialDIDef
        + Bindings.binding[TestDependency0, TestImpl0]
        )

      val combinedModules = Vector(mod1, mod2, mod3, mod4, mod5).combineAll

      val plusModules = mod5 |+| mod4 |+| mod3 |+| mod2 |+| mod1

      val complexModule = TrivialDIDef
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
