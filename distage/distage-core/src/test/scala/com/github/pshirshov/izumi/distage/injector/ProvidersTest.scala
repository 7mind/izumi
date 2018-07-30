package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.ProviderCases.{ProviderCase3, ProviderCase2}
import distage.{Id, ModuleDef}
import org.scalatest.WordSpec

class ProvidersTest extends WordSpec with MkInjector {

  "instantiate provider bindings" in {
    import ProviderCase2._

    val definition = new ModuleDef {
      make[TestClass].from((a: Dependency1) => new TestClass(null))
      make[Dependency1].from(() => new Dependency1Sub {})
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    assert(context.parent.exists(_.plan.steps.nonEmpty))
    val instantiated = context.get[TestClass]
    assert(instantiated.b == null)
  }

  "support named bindings in method reference providers" in {
    import ProviderCase3._

    val definition = new ModuleDef {
      make[TestDependency].named("classdeftypeann1")
      make[TestClass].from(implType _)
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val dependency = context.get[TestDependency]("classdeftypeann1")
    val instantiated = context.get[TestClass]

    assert(instantiated.a == dependency)
  }

  "support named bindings in lambda providers" in {
    import ProviderCase3._

    val definition = new ModuleDef {
      make[TestDependency].named("classdeftypeann1")
      make[TestClass].from { t: TestDependency@Id("classdeftypeann1") => new TestClass(t) }
    }

    val injector = mkInjector()
    val context = injector.produce(injector.plan(definition))

    val dependency = context.get[TestDependency]("classdeftypeann1")
    val instantiated = context.get[TestClass]

    assert(instantiated.a == dependency)
  }
}
