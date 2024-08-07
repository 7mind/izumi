package izumi.distage.injector

import distage.{Injector, ModuleDef}
import izumi.distage.fixtures.BasicCases.BasicCase1
import izumi.distage.model.PlannerInput
import org.scalatest.wordspec.AnyWordSpec

class PrivateBindingsTest extends AnyWordSpec with MkInjector {
  "Support private bindings" in {
    import BasicCase1.*

    val injector = mkInjector()

    val def1 = PlannerInput.everything(new ModuleDef {
      make[TestDependency0].from[TestImpl0].confined
    })
    val plan1 = injector.planUnsafe(def1)
    val loc = injector.produce(plan1).unsafeGet()
    assert(loc.find[TestDependency0].nonEmpty)

    val injector2 = Injector.inherit(loc)

    val def2 = PlannerInput.everything(new ModuleDef {
      make[JustTrait].from[Impl0]
    })
    val plan2 = injector2.planUnsafe(def2)
    val loc2 = injector2.produce(plan2).unsafeGet()

    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].isEmpty)
  }

}
