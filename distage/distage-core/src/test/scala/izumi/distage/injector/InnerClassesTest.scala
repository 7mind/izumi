package izumi.distage.injector

import izumi.distage.constructors.FactoryConstructor
import izumi.distage.fixtures.InnerClassCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{Activation, ModuleDef}
import org.scalatest.wordspec.AnyWordSpec

class InnerClassesTest extends AnyWordSpec with MkInjector {
  "can instantiate inner classes from stable objects where the classes are inherited from a trait" in {
    import InnerClassStablePathsCase._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[StableObjectInheritingTrait.TestDependency]
      make[StableObjectInheritingTrait1.TestDependency]
    })

    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[StableObjectInheritingTrait.TestDependency] == StableObjectInheritingTrait.TestDependency())
    assert(context.get[StableObjectInheritingTrait1.TestDependency] == StableObjectInheritingTrait1.TestDependency())
    assert(context.get[StableObjectInheritingTrait1.TestDependency] != context.get[StableObjectInheritingTrait.TestDependency])
  }

  "can instantiate inner classes from stable objects where the classes are inherited from a trait and depend on types defined inside trait" in {
    import InnerClassStablePathsCase._
    import StableObjectInheritingTrait._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestDependency]
      make[TestClass]
    })

    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[TestClass] == TestClass(TestDependency()))
  }

  "can support path-dependant injections with injector lookup" in {
    import InnerClassUnstablePathsCase._

    val testProviderModule = new TestModule

    val definition = PlannerInput.noGc(new ModuleDef {
      make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
      make[testProviderModule.TestDependency]
      make[testProviderModule.TestClass]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[testProviderModule.TestClass].a.isInstanceOf[testProviderModule.TestDependency])
  }

  "can handle function local path-dependent injections" in {
    def someFunction() = {
      import InnerClassUnstablePathsCase._

      val testProviderModule = new TestModule

      val definition = PlannerInput.noGc(new ModuleDef {
//        make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
        make[testProviderModule.TestClass]
        make[testProviderModule.TestDependency]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan).unsafeGet()

      assert(context.get[testProviderModule.TestClass].a.isInstanceOf[testProviderModule.TestDependency])
    }

    someFunction()
  }

  "progression test: can't handle concrete type projections as prefixes in path-dependent injections" in {
    assertTypeError("""
      import InnerClassUnstablePathsCase._

      val definition = PlannerInput.noGc(new ModuleDef {
        make[TestModule]
        make[TestModule#TestClass]
        make[TestModule#TestDependency]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan).unsafeGet()

      assert(context.get[TestModule#TestClass].a.isInstanceOf[TestModule#TestDependency])
      """)
  }

  "support path-dependent by-name injections" in {
    import InnerClassByNameCase._

    val testProviderModule = new TestModule

    val definition = PlannerInput.noGc(new ModuleDef {
//      make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
      make[testProviderModule.TestDependency]
      make[testProviderModule.TestClass]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[testProviderModule.TestClass].aValue.isInstanceOf[testProviderModule.TestDependency])
  }

  "can handle inner path-dependent injections (macros can)" in {
    new InnerPathDepTest().testCase
  }

  "can handle class local path-dependent injections (macros can)" in {
    val definition = PlannerInput.noGc(new ModuleDef {
      make[TopLevelPathDepTest.type].from[TopLevelPathDepTest.type](TopLevelPathDepTest: TopLevelPathDepTest.type)
      make[TopLevelPathDepTest.TestClass]
      make[TopLevelPathDepTest.TestDependency]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TopLevelPathDepTest.TestClass].a != null)
  }

  "Can handle factories inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait (macros can't)" in {
    import InnerClassStablePathsCase.StableObjectInheritingTrait.{TestClass, TestDependency, TestFactory}

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestFactory]
    })

    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[TestFactory].mk(TestDependency()) == TestClass(TestDependency()))
  }

  "can now find proper constructor for by-name circular dependencies inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait" in {
    import InnerClassStablePathsCase._
    import StableObjectInheritingTrait._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[ByNameCircular1]
      make[ByNameCircular2]
    })

    val context = mkNoCglibInjector().produce(definition).unsafeGet()

    assert(context.get[ByNameCircular1] != null)
    assert(context.get[ByNameCircular1].circular2 eq context.get[ByNameCircular2])
  }

  "can now handle path-dependent factories" in {
    import InnerClassUnstablePathsCase._
    val testProviderModule = new TestModule

    FactoryConstructor[testProviderModule.TestFactory]

    val definition = PlannerInput.target[testProviderModule.TestFactory](
      new ModuleDef {
        make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
        make[testProviderModule.TestFactory]
      },
      Activation.empty,
    )

    val context = mkInjector().produce(definition).unsafeGet()
    assert(context.instances.size == 2)

    assert(context.get[testProviderModule.TestFactory].mk(testProviderModule.TestDependency()) == testProviderModule.TestClass(testProviderModule.TestDependency()))
  }

  class InnerPathDepTest extends InnerClassUnstablePathsCase.TestModule {
    private val definition = PlannerInput.noGc(new ModuleDef {
      make[InnerPathDepTest.this.type].from[InnerPathDepTest.this.type](InnerPathDepTest.this: InnerPathDepTest.this.type)
      make[TestClass]
      make[TestDependency]
    })

    def testCase = {
      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[TestClass].a != null)
    }
  }

  object TopLevelPathDepTest extends InnerClassUnstablePathsCase.TestModule

}
