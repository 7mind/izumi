package izumi.distage.injector

import izumi.distage.constructors.FactoryConstructor
import izumi.distage.fixtures.InnerClassCases.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.{Activation, ModuleDef}
import org.scalatest.wordspec.AnyWordSpec

class InnerClassesTest extends AnyWordSpec with MkInjector {
  "can instantiate inner classes from stable objects where the classes are inherited from a trait" in {
    import InnerClassStablePathsCase._

    val definition = PlannerInput.everything(new ModuleDef {
      make[StableObjectInheritingTrait.TestDependency]
      make[StableObjectInheritingTrait1.TestDependency]
    })

    val context = mkNoCyclesInjector().produce(definition).unsafeGet()

    assert(context.get[StableObjectInheritingTrait.TestDependency] == StableObjectInheritingTrait.TestDependency())
    assert(context.get[StableObjectInheritingTrait1.TestDependency] == StableObjectInheritingTrait1.TestDependency())
    assert(context.get[StableObjectInheritingTrait1.TestDependency] != context.get[StableObjectInheritingTrait.TestDependency])
  }

  "can instantiate inner classes from stable objects where the classes are inherited from a trait and depend on types defined inside trait" in {
    import InnerClassStablePathsCase._
    import StableObjectInheritingTrait._

    val definition = PlannerInput.everything(new ModuleDef {
      make[TestDependency]
      make[TestClass]
    })

    val context = mkNoCyclesInjector().produce(definition).unsafeGet()

    assert(context.get[TestClass] == TestClass(TestDependency()))
  }

  "can support path-dependant injections with injector lookup" in {
    import InnerClassUnstablePathsCase._

    val testProviderModule = new TestModule

    val definition = PlannerInput.everything(new ModuleDef {
      make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
      make[testProviderModule.TestDependency]
      make[testProviderModule.TestClass[testProviderModule.type]]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[testProviderModule.TestClass[testProviderModule.type]].a.isInstanceOf[testProviderModule.TestDependency])
  }

  "can handle function local path-dependent injections" in {
    def someFunction() = {
      import InnerClassUnstablePathsCase._

      val testProviderModule = new TestModule

      val definition = PlannerInput.everything(new ModuleDef {
        make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
        make[testProviderModule.TestClass[testProviderModule.type]]
        make[testProviderModule.TestDependency]
      })

      val injector = mkNoCyclesInjector()
      val plan = injector.planUnsafe(definition)

      val context = injector.produce(plan).unsafeGet()

      assert(context.get[testProviderModule.TestClass[testProviderModule.type]].a.isInstanceOf[testProviderModule.TestDependency])
      assert(context.get[testProviderModule.TestClass[testProviderModule.type]].t == testProviderModule)
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

    val definition = PlannerInput.everything(new ModuleDef {
      make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
      make[testProviderModule.TestDependency]
      make[testProviderModule.TestClass[testProviderModule.type]]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[testProviderModule.TestClass[testProviderModule.type]].aValue.isInstanceOf[testProviderModule.TestDependency])
    assert(context.get[testProviderModule.TestClass[testProviderModule.type]].t == testProviderModule)
  }

  "can handle inner path-dependent injections" in {
    new InnerPathDepTest().testCase
  }

  "can handle class local path-dependent injections" in {
    val definition = PlannerInput.everything(new ModuleDef {
      make[TopLevelPathDepTest.type].from[TopLevelPathDepTest.type](TopLevelPathDepTest: TopLevelPathDepTest.type)
      make[TopLevelPathDepTest.TestClass[TopLevelPathDepTest.type]]
      make[TopLevelPathDepTest.TestDependency]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)

    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TopLevelPathDepTest.TestClass[TopLevelPathDepTest.type]].a != null)
    assert(context.get[TopLevelPathDepTest.TestClass[TopLevelPathDepTest.type]].t == TopLevelPathDepTest)
  }

  "Can handle factories inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait (macros can't)" in {
    import InnerClassStablePathsCase.StableObjectInheritingTrait.{TestClass, TestDependency, TestFactory}

    val definition = PlannerInput.everything(new ModuleDef {
      makeFactory[TestFactory]
    })

    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[TestFactory].mk(TestDependency()) == TestClass(TestDependency()))
  }

  "can now find proper constructor for by-name circular dependencies inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait" in {
    import InnerClassStablePathsCase._
    import StableObjectInheritingTrait._

    val definition = PlannerInput.everything(new ModuleDef {
      make[ByNameCircular1]
      make[ByNameCircular2]
    })

    val context = mkNoProxiesInjector().produce(definition).unsafeGet()

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
        makeFactory[testProviderModule.TestFactory]
      },
      Activation.empty,
    )

    val context = mkInjector().produce(definition).unsafeGet()
    assert(context.instances.size == 2)

    assert(
      context.get[testProviderModule.TestFactory].mk(testProviderModule.TestDependency()) ==
      testProviderModule.TestClass(testProviderModule.TestDependency(), testProviderModule)
    )
  }

  class InnerPathDepTest extends InnerClassUnstablePathsCase.TestModule {
    private val definition = PlannerInput.everything(new ModuleDef {
      make[InnerPathDepTest.this.type].from[InnerPathDepTest.this.type](InnerPathDepTest.this: InnerPathDepTest.this.type)
      make[InnerPathDepTest.type].from[InnerPathDepTest.type]
      make[TestClass[InnerPathDepTest.this.type]]
      make[TestDependency]
    })

    def testCase = {
      val injector = mkInjector()
      val plan = injector.planUnsafe(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[TestClass[InnerPathDepTest.this.type]].a != null)
      assert(context.get[InnerPathDepTest.this.type] ne context.get[InnerPathDepTest.type])
    }
  }
  object InnerPathDepTest extends InnerClassUnstablePathsCase.TestModule

  object TopLevelPathDepTest extends InnerClassUnstablePathsCase.TestModule

}
