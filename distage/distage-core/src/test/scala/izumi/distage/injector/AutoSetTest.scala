package izumi.distage.injector

import org.scalatest.WordSpec
import izumi.distage.fixtures.SetCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.planning.PlanningHook
import izumi.distage.planning.AssignableFromAutoSetHook
import distage.{BootstrapModuleDef, Injector, ModuleDef}

class AutoSetTest extends WordSpec with MkInjector {

  "AutoSets preserve dependency order" in {
    import SetCase3._

    val definition = new ModuleDef {
      make[ServiceA]
      make[ServiceB]
      make[ServiceC]
      make[ServiceD]
    }

    val injector = Injector.Standard(new BootstrapModuleDef {
      many[AutoCloseable]
      many[PlanningHook]
        .add(new AssignableFromAutoSetHook[Ordered, Ordered](identity))
    })

    val autoCloseableSet = injector.produceUnsafe(PlannerInput.noGc(definition)).get[Set[Ordered]]

    assert(autoCloseableSet.toSeq == autoCloseableSet.toSeq.sortBy(_.order))
  }

}
