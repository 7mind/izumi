package izumi.distage.staticinjector

import izumi.distage.fixtures.ImplicitCases.ImplicitCase2
import izumi.distage.injector.MkInjector
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import org.scalatest.wordspec.AnyWordSpec

class StaticImplicitInjectionTest extends AnyWordSpec with MkInjector {

  "Handle multiple parameter lists" in {
    import ImplicitCase2._

    val injector = mkNoCyclesInjector()

    val definition = new ModuleDef {
      make[TestDependency1]
      make[TestDependency2]
      make[TestDependency3]
      make[TestClass]
    }
    val plan = injector.plan(PlannerInput.noGc(definition))
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TestClass].a != null)
    assert(context.get[TestClass].b != null)
    assert(context.get[TestClass].c != null)
    assert(context.get[TestClass].d != null)
  }

  "Implicit parameters are injected from the DI object graph, not from Scala's lexical implicit scope" in {
    import ImplicitCase2._

    val injector = mkNoCyclesInjector()

    val definition = new ModuleDef {
      implicit val testDependency3: TestDependency3 = new TestDependency3

      make[TestDependency1]
      make[TestDependency2]
      make[TestDependency3]
      make[TestClass]
    }
    val plan = injector.plan(PlannerInput.noGc(definition))
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TestClass].b == context.get[TestClass].d)
  }

}
