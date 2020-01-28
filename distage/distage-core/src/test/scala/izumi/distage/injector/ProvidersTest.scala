package izumi.distage.injector

import izumi.distage.fixtures.ProviderCases.{ProviderCase2, ProviderCase3}
import izumi.distage.model.PlannerInput
import distage.{Id, ModuleDef}
import org.scalatest.wordspec.AnyWordSpec

class ProvidersTest extends AnyWordSpec with MkInjector {

  "instantiate provider bindings" in {
    import ProviderCase2._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestClass].from((_: Dependency1) => new TestClass(null))
      make[Dependency1].from(() => new Dependency1Sub {})
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan).unsafeGet()
    assert(context.parent.exists(_.plan.steps.nonEmpty))
    val instantiated = context.get[TestClass]
    assert(instantiated.b == null)
  }

  "support named bindings in method reference providers" in {
    import ProviderCase3._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestDependency].named("classdeftypeann1")
      make[TestClass].from(implType _)
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val dependency = context.get[TestDependency]("classdeftypeann1")
    val instantiated = context.get[TestClass]

    assert(instantiated.a == dependency)
  }

  "support named bindings in lambda providers" in {
    import ProviderCase3._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestDependency].named("classdeftypeann1")
      make[TestClass].from { t: TestDependency@Id("classdeftypeann1") => new TestClass(t) }
    })

    val injector = mkInjector()
    val context = injector.produce(injector.plan(definition)).unsafeGet()

    val dependency = context.get[TestDependency]("classdeftypeann1")
    val instantiated = context.get[TestClass]

    assert(instantiated.a == dependency)
  }

}
