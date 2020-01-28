package izumi.distage.staticinjector

import izumi.distage.fixtures.InnerClassCases.{InnerClassStablePathsCase, InnerClassUnstablePathsCase}
import izumi.distage.injector.MkInjector
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import org.scalatest.wordspec.AnyWordSpec

class StaticInnerClassesTest extends AnyWordSpec with MkInjector {

  "macros can handle class local path-dependent injections" in {
    val definition = new ModuleDef {
      make[TopLevelPathDepTest.TestClass]
      make[TopLevelPathDepTest.TestDependency]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(PlannerInput.noGc(definition))

    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TopLevelPathDepTest.TestClass].a != null)
  }

  "macros can handle inner path-dependent injections" in {
    new InnerPathDepTest().testCase
  }

  "macros can handle function local path-dependent injections" in {
    import InnerClassUnstablePathsCase._

    val testModule = new TestModule

    val definition = new ModuleDef {
      make[testModule.TestClass]
      make[testModule.TestDependency]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(PlannerInput.noGc(definition))

    val context = injector.produce(plan).unsafeGet()

    assert(context.get[testModule.TestClass].a != null)
  }

  "macros can instantiate inner classes from stable objects where the classes are inherited from a trait" in {
    import InnerClassStablePathsCase._
    import StableObjectInheritingTrait._

    val definition = new ModuleDef {
      make[TestDependency]
    }

    val context = mkNoCyclesInjector().produce(PlannerInput.noGc(definition)).unsafeGet()

    assert(context.get[TestDependency] == TestDependency())
  }

  "macros can instantiate inner classes from stable objects where the classes are inherited from a trait and depend on types defined inside trait" in {
    import InnerClassStablePathsCase._
    import StableObjectInheritingTrait._

    val definition = new ModuleDef {
      make[TestDependency]
      make[TestClass]
    }

    val context = mkNoCyclesInjector().produce(PlannerInput.noGc(definition)).unsafeGet()

    assert(context.get[TestClass] == TestClass(TestDependency()))
  }

  "progression test: compile-time ReflectionProvider can't handle factories inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait" in {
    assertTypeError("""
      import InnerClassCase2._
      import StableObjectInheritingTrait._

      val definition = new ModuleDef {
        make[TestFactory]
      }

      val context = mkStaticInjector().produce(PlannerInput.noGc(definition))

      assert(context.get[TestFactory].mk(TestDependency()) == TestClass(TestDependency()))
      """)
  }

  class InnerPathDepTest extends InnerClassUnstablePathsCase.TestModule {
    private val definition = new ModuleDef {
      make[TestClass]
      make[TestDependency]
    }

    def testCase = {
      val injector = mkNoCyclesInjector()
      val plan = injector.plan(PlannerInput.noGc(definition))

      val context = injector.produce(plan).unsafeGet()

      assert(context.get[TestClass].a != null)
    }
  }

  object TopLevelPathDepTest extends InnerClassUnstablePathsCase.TestModule
}
