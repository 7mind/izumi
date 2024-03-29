package izumi.distage.injector

import distage.{BootstrapModuleDef, Injector, ModuleDef}
import izumi.distage.fixtures.SetCases.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.planning.PlanningHook
import izumi.distage.planning.AutoSetHook
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class AutoSetTest extends AnyWordSpec with MkInjector {

  "AutoSets preserve dependency order" in {
    import SetCase3.*

    val definition = new ModuleDef {
      make[ServiceA]
      make[ServiceB]
      make[ServiceC]
      make[ServiceD]
    }

    val injector = Injector[Identity](new BootstrapModuleDef {
      many[PlanningHook]
        .add(AutoSetHook[Ordered]("order"))
    })

    val autoset = injector.produce(PlannerInput.everything(definition)).unsafeGet().get[Set[Ordered]]("order")

    assert(autoset.toSeq == autoset.toSeq.sortBy(_.order))
  }

  "AutoSets collect instances with the same type but different implementations" in {
    val definition = new ModuleDef {
      make[Int].fromValue(1)
      make[Int].named("x").fromValue(2)
      many[Int]
        .named("nonauto")
        .addValue(3)
        .addValue(4)
        .addValue(5)
    }

    val injector = Injector[Identity](new BootstrapModuleDef {
      many[PlanningHook]
        .add(AutoSetHook[Int])
    })

    val autoset = injector.produce(PlannerInput.everything(definition)).unsafeGet().get[Set[Int]]

    assert(autoset == Set(1, 2, 3, 4, 5))
  }

}
