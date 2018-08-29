package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.ImplicitCases.{ImplicitCase1, ImplicitCase2}
import distage.{ModuleBase, ModuleDef}
import org.scalatest.WordSpec
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks.discard

class ImplicitInjectionTest extends WordSpec with MkInjector {

  "Handle multiple parameter lists" in {
    import ImplicitCase2._

    val definition = new ModuleDef {
      make[TestDependency2]
      make[TestDependency1]
      make[TestDependency3]
      make[TestClass]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[TestClass].a != null)
    assert(context.get[TestClass].b != null)
    assert(context.get[TestClass].c != null)
    assert(context.get[TestClass].d != null)
  }

  "populates implicit parameters in class constructor from explicit DI context instead of scala's implicit resolution" in {
    import ImplicitCase1._

    val definition = new ModuleDef {
      make[TestClass]
      make[Dep]
      make[DummyImplicit].from[MyDummyImplicit]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val instantiated = context.get[TestClass]

    assert(instantiated.dummyImplicit.isInstanceOf[MyDummyImplicit])
    assert(instantiated.dummyImplicit.asInstanceOf[MyDummyImplicit].imADummy)
  }

  "Progression test: As of now, implicit parameters are injected from the DI context, not from Scala's lexical implicit scope" in {
    import ImplicitCase2._

    val definition: ModuleBase = new ModuleDef {
      implicit val testDependency3: TestDependency3 = new TestDependency3
      discard(testDependency3)

      make[TestDependency1]
      make[TestDependency2]
      make[TestDependency3]
      make[TestClass]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[TestClass].b == context.get[TestClass].d)
  }
}
