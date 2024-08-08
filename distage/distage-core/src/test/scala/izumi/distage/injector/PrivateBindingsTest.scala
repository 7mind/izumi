package izumi.distage.injector

import distage.{Injector, LocatorPrivacy, ModuleDef}
import izumi.distage.fixtures.BasicCases.BasicCase1
import izumi.distage.model.PlannerInput
import org.scalatest.wordspec.AnyWordSpec
import BasicCase1.*

class PrivateBindingsTest extends AnyWordSpec with MkInjector {
  "Support private bindings in public-by-default mode" in {
    val loc2 = prepareInheritedLocator(
      new ModuleDef {
        make[TestDependency0].from[TestImpl0].confined
      },
      LocatorPrivacy.PublicByDefault,
    )

    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].isEmpty)
  }

  "Support public bindings in public-by-default mode" in {
    val loc2 = prepareInheritedLocator(
      new ModuleDef {
        make[TestDependency0].from[TestImpl0]
      },
      LocatorPrivacy.PublicByDefault,
    )

    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].nonEmpty)
  }

  "Support private bindings in private-by-default mode" in {
    val loc2 = prepareInheritedLocator(
      new ModuleDef {
        make[TestDependency0].from[TestImpl0]
      },
      LocatorPrivacy.PrivateByDefault,
    )

    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].isEmpty)
  }

  "Support public bindings in private-by-default mode" in {
    val loc2 = prepareInheritedLocator(
      new ModuleDef {
        make[TestDependency0].from[TestImpl0].exposed
      },
      LocatorPrivacy.PrivateByDefault,
    )

    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].nonEmpty)
  }

  private def prepareInheritedLocator(m: ModuleDef, locatorPrivacy: LocatorPrivacy) = {
    val injector = mkInjector()

    val def1 = PlannerInput
      .everything(m)
      .withLocatorPrivacy(locatorPrivacy)

    val plan1 = injector.planUnsafe(def1)
    val loc = injector.produce(plan1).unsafeGet()
    assert(loc.find[TestDependency0].nonEmpty)

    val injector2 = Injector.inherit(loc)

    val def2 = PlannerInput.everything(new ModuleDef {
      make[JustTrait].from[Impl0]
    })

    val plan2 = injector2.planUnsafe(def2)
    val loc2 = injector2.produce(plan2).unsafeGet()
    loc2
  }

}
