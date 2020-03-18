package izumi.distage.compat

import cats.implicits._
import izumi.distage.fixtures.BasicCases._
import izumi.distage.model.definition.Bindings.binding
import izumi.distage.model.definition._
import org.scalatest.wordspec.AnyWordSpec

final class ModuleBaseInstancesTest extends AnyWordSpec {
  "cats instances for ContextDefinition" should {
    "allow monoidal & Eq.eqv operations between different types of binding dsls" in {
      import BasicCase1._

      val mod1 = new ModuleDef {
        make[TestClass]
      }

      val mod2 = new ModuleDef {
        make[TestCaseClass2]
      }

      val mod3_1: Module = new ModuleDef {
        make[TestDependency1]
      }

      val mod3_2 = Module.empty

      val mod3 = (mod3_1 |+| mod3_2) :+ binding[NotInContext]

      val mod4 = Module.make(Set(
        binding(TestInstanceBinding())
      ))

      val moduleDef = new ModuleDef {}
      val mod5 = moduleDef :+ Bindings.binding[TestDependency0, TestImpl0]

      val combinedModules = Vector(mod1, mod2, mod3, mod4, mod5).combineAll

      val plusModules = mod5 |+| mod4 |+| mod3 |+| mod2 |+| mod1

      val complexModule = Module.make(Set(
        Bindings.binding[TestClass]
        , Bindings.binding[TestDependency0, TestImpl0]
        , Bindings.binding[TestCaseClass2]
        , Bindings.binding(TestInstanceBinding())
      )) |+| mod3 // function pointer equality on magic trait providers


      assert(combinedModules eqv complexModule)
      assert(plusModules eqv complexModule)
    }
  }
}
