package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.InnerClassCases.{InnerClassCase1, InnerClassCase2}
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import org.scalatest.WordSpec

import scala.util.Try

class InnerClassesTest extends WordSpec with MkInjector {
  "can instantiate inner classes from stable objects where the classes are inherited from a trait" in {
    import InnerClassCase2._
    import StableObjectInheritingTrait._

    val definition = new ModuleDef {
      make[TestDependency]
      make[StableObjectInheritingTrait1.TestDependency]
    }

    val context = mkInjector().produce(definition)

    assert(context.get[TestDependency] == TestDependency())
    assert(context.get[StableObjectInheritingTrait1.TestDependency] == StableObjectInheritingTrait1.TestDependency())
    assert(context.get[StableObjectInheritingTrait1.TestDependency] != context.get[TestDependency])
  }

  "can instantiate inner classes from stable objects where the classes are inherited from a trait and depend on types defined inside trait" in {
    import InnerClassCase2._
    import StableObjectInheritingTrait._

    val definition = new ModuleDef {
      make[TestDependency]
      make[TestClass]
    }

    val context = mkInjector().produce(definition)

    assert(context.get[TestClass] == TestClass(TestDependency()))
  }

  "can support path-dependant injections with injector lookup" in {
    import InnerClassCase1._

    val testProviderModule = new TestModule

    val definition = new ModuleDef {
      make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
      make[testProviderModule.TestClass]
      make[testProviderModule.TestDependency]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)

    assert(context.get[testProviderModule.TestClass].a.isInstanceOf[testProviderModule.TestDependency])

  }

  "progression test: classstrategy can't handle class local path-dependent injections (macros can)" in {
    val fail = Try {
      val definition = new ModuleDef {
        make[TopLevelPathDepTest.TestClass]
        make[TopLevelPathDepTest.TestDependency]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[TopLevelPathDepTest.TestClass].a != null)
    }
    assert(fail.isFailure)
  }

  "progression test: can't handle inner path-dependent injections (macros can)" in {
    val fail = Try {
      new InnerPathDepTest().testCase
    }
    assert(fail.isFailure)
  }

  "progression test: classstrategy can't handle function local path-dependent injections (macros can't)" in {
    val fail = Try {
      import InnerClassCase1._

      val testProviderModule = new TestModule

      val definition = new ModuleDef {
        make[testProviderModule.TestClass]
        make[testProviderModule.TestDependency]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[testProviderModule.TestClass].a.isInstanceOf[testProviderModule.TestDependency])
    }
    assert(fail.isFailure)
  }

  "progression test: ReflectionProvider can't handle factories inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait (macros can't)" in {
    val fail = Try {
      import InnerClassCase2._
      import StableObjectInheritingTrait._

      val definition = new ModuleDef {
        make[TestFactory]
      }

      val context = mkInjector().produce(definition)

      assert(context.get[TestFactory].mk(TestDependency()) == TestClass(TestDependency()))
    }
    assert(fail.isFailure)
  }

  class InnerPathDepTest extends InnerClassCase1.TestModule {
    private val definition = new ModuleDef {
      make[TestClass]
      make[TestDependency]
    }

    def testCase = {
      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[TestClass].a != null)
    }
  }

  object TopLevelPathDepTest extends InnerClassCase1.TestModule

}
