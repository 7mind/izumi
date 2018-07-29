package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.Fixtures.{TypesCase1, ProviderCase1, TraitCase2}
import distage.{Id, ModuleBase, ModuleDef}
import org.scalatest.WordSpec

import scala.util.Try

class AdvancedTypesTest extends WordSpec with MkInjector {

  "support generics" in {
    import TypesCase1._

    val definition: ModuleBase = new ModuleDef {
      make[List[Dep]].named("As").from(List(DepA()))
      make[List[Dep]].named("Bs").from(List(DepB()))
      make[List[DepA]].from(List(DepA(), DepA(), DepA()))
      make[TestClass[DepA]]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[List[Dep]]("As").forall(_.isInstanceOf[DepA]))
    assert(context.get[List[DepA]].forall(_.isInstanceOf[DepA]))
    assert(context.get[List[Dep]]("Bs").forall(_.isInstanceOf[DepB]))
    assert(context.get[TestClass[DepA]].inner == context.get[List[DepA]])
  }

  "support classes with typealiases" in {
    import TypesCase1._

    val definition = new ModuleDef {
      make[DepA]
      make[TestClass2[TypeAliasDepA]]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[TestClass2[TypeAliasDepA]].inner.isInstanceOf[TypeAliasDepA])
  }

  "support traits with typealiases" in {
    import TypesCase1._

    val definition = new ModuleDef {
      make[DepA]
      make[TestTrait]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[TestTrait].dep.isInstanceOf[TypeAliasDepA])
  }

  "type annotations in di keys do not result in different keys" in {
    import TraitCase2._

    val definition = new ModuleDef {
      make[Dependency1 @Id("special")]
      make[Trait1]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instantiated = context.get[Dependency1]
    val instantiated1 = context.get[Dependency1@Id("special")]

    assert(instantiated eq instantiated1)
  }

  "progression test: cglib can't handle class local path-dependent injections (macros can)" in {
    val fail = Try {
      val definition = new ModuleDef {
        make[TopLevelPathDepTest.TestClass]
        make[TopLevelPathDepTest.TestDependency]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[TopLevelPathDepTest.TestClass].a != null)
    }.isFailure
    assert(fail)
  }

  "progression test: cglib can't handle inner path-dependent injections (macros can)" in {
    val fail = Try {
      new InnerPathDepTest().testCase
    }.isFailure
    assert(fail)
  }

  "progression test: cglib can't handle function local path-dependent injections" in {
    val fail = Try {
      import ProviderCase1._

      val testProviderModule = new TestProviderModule

      val definition = new ModuleDef {
        make[testProviderModule.TestClass]
        make[testProviderModule.TestDependency]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[testProviderModule.TestClass].a.isInstanceOf[testProviderModule.TestDependency])
    }.isFailure
    assert(fail)
  }

  class InnerPathDepTest extends ProviderCase1.TestProviderModule {
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

  object TopLevelPathDepTest extends ProviderCase1.TestProviderModule
}
