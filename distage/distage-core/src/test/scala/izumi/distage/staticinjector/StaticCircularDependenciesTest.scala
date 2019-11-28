package izumi.distage.staticinjector

import distage.ModuleBase
import izumi.distage.fixtures.CircularCases.{CircularCase1, CircularCase2}
import izumi.distage.injector.MkInjector
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import org.scalatest.WordSpec

class StaticCircularDependenciesTest extends WordSpec with MkInjector {

  "support circular dependencies (with cglib on JVM)" in {
    import CircularCase1._

    val definition: ModuleBase = new ModuleDef {
      make[Circular2]
      make[Circular1]
    }

    val injector = mkInjectorWithProxy()
    val plan = injector.plan(PlannerInput.noGc(definition))
    val context = injector.produceUnsafe(plan)

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)
    assert(context.get[Circular2].arg != null)
  }

  "support circular dependencies in providers (with cglib on JVM)" in {
    import CircularCase1._

    val definition: ModuleBase = new ModuleDef {
      make[Circular2].from { c: Circular1 => new Circular2(c) }
      make[Circular1].from { c: Circular2 => new Circular1 { override val arg: Circular2 = c } }
    }

    val injector = mkInjectorWithProxy()
    val plan = injector.plan(PlannerInput.noGc(definition))
    val context = injector.produceUnsafe(plan)

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)
    assert(context.get[Circular2].arg != null)
  }

  "support complex circular dependencies (with cglib on JVM)" in {
    import CircularCase2._

    val definition: ModuleBase = new ModuleDef {
      make[Circular3]
      make[Circular1]
      make[Circular2]
      make[Circular5]
      make[Circular4]
    }

    val injector = mkInjectorWithProxy()
    val plan = injector.plan(PlannerInput.noGc(definition))
    val context = injector.produceUnsafe(plan)
    val c3 = context.get[Circular3]
    val traitArg = c3.arg

    assert(traitArg != null && traitArg.isInstanceOf[Circular4])
    assert(c3.method == 2L)
    assert(traitArg.testVal == 1)
    assert(context.instances.nonEmpty)
    assert(context.get[Circular4].factoryFun(context.get[Circular4], context.get[Circular5]) != null)
  }
}
