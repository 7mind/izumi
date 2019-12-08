package izumi.distage.injector

import izumi.distage.fixtures.ImplicitCases.{ImplicitCase1, ImplicitCase2}
import izumi.distage.model.PlannerInput
import izumi.fundamentals.platform.language.Quirks.discard
import distage.ModuleDef
import org.scalatest.wordspec.AnyWordSpec

class ImplicitInjectionTest extends AnyWordSpec with MkInjector {

  "Handle multiple parameter lists" in {
    import ImplicitCase2._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestDependency2]
      make[TestDependency1]
      make[TestDependency3]
      make[TestClass]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[TestClass].a != null)
    assert(context.get[TestClass].b != null)
    assert(context.get[TestClass].c != null)
    assert(context.get[TestClass].d != null)
  }

  "populates implicit parameters in class constructor from explicit DI object graph instead of scala's implicit resolution" in {
    import ImplicitCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestClass]
      make[Dep]
      make[DummyImplicit].from[MyDummyImplicit]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produceUnsafe(plan)
    val instantiated = context.get[TestClass]

    assert(instantiated.dummyImplicit.isInstanceOf[MyDummyImplicit])
    assert(instantiated.dummyImplicit.asInstanceOf[MyDummyImplicit].imADummy)
  }

  "Progression test: As of now, implicit parameters are injected from DI object graph, not from Scala's lexical implicit scope" in {
    import ImplicitCase2._

    val definition = PlannerInput.noGc(new ModuleDef {
      implicit val testDependency3: TestDependency3 = new TestDependency3
      discard(testDependency3)

      make[TestDependency1]
      make[TestDependency2]
      make[TestDependency3]
      make[TestClass]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[TestClass].b == context.get[TestClass].d)
  }
}
