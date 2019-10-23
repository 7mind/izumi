package izumi.distage.staticinjector

import izumi.distage.constructors.StaticModuleDef
import izumi.distage.fixtures.ImplicitCases.ImplicitCase2
import izumi.distage.injector.MkInjector
import izumi.distage.model.PlannerInput
import org.scalatest.WordSpec

class StaticImplicitInjectionTest extends WordSpec with MkInjector {

  "Handle multiple parameter lists" in {
    import ImplicitCase2._

    val injector = mkStaticInjector()

    val definition = new StaticModuleDef {
      stat[TestDependency1]
      stat[TestDependency2]
      stat[TestDependency3]
      stat[TestClass]
    }
    val plan = injector.plan(PlannerInput.noGc(definition))
    val context = injector.produceUnsafe(plan)

    assert(context.get[TestClass].a != null)
    assert(context.get[TestClass].b != null)
    assert(context.get[TestClass].c != null)
    assert(context.get[TestClass].d != null)
  }

  "Implicit parameters are injected from the DI object graph, not from Scala's lexical implicit scope" in {
    import ImplicitCase2._

    val injector = mkStaticInjector()

    val definition = new StaticModuleDef {
      implicit val testDependency3: TestDependency3 = new TestDependency3

      stat[TestDependency1]
      stat[TestDependency2]
      stat[TestDependency3]
      stat[TestClass]
    }
    val plan = injector.plan(PlannerInput.noGc(definition))
    val context = injector.produceUnsafe(plan)

    assert(context.get[TestClass].b == context.get[TestClass].d)
  }

}
