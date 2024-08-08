package izumi.distage.injector

import distage.{Activation, Injector, LocatorPrivacy, ModuleDef}
import izumi.distage.fixtures.BasicCases.BasicCase1
import izumi.distage.model.PlannerInput
import org.scalatest.wordspec.AnyWordSpec
import BasicCase1.*

class PrivateBindingsTest extends AnyWordSpec with MkInjector {
  "Support private bindings in public-by-default mode" in {
    val def1 = PlannerInput
      .everything(new ModuleDef {
        make[TestDependency0].from[TestImpl0].confined
      })
      .withLocatorPrivacy(LocatorPrivacy.PublicByDefault)

    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestDependency0].nonEmpty)
    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].isEmpty)
  }

  "Support public bindings in public-by-default mode" in {
    val def1 = PlannerInput
      .everything(new ModuleDef {
        make[TestDependency0].from[TestImpl0]
      })
      .withLocatorPrivacy(LocatorPrivacy.PublicByDefault)
    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestDependency0].nonEmpty)
    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].nonEmpty)
  }

  "Support private bindings in private-by-default mode" in {
    val def1 = PlannerInput
      .everything(new ModuleDef {
        make[TestDependency0].from[TestImpl0]
      })
      .withLocatorPrivacy(LocatorPrivacy.PrivateByDefault)

    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestDependency0].nonEmpty)
    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].isEmpty)
  }

  "Support public bindings in private-by-default mode" in {
    val def1 = PlannerInput
      .everything(new ModuleDef {
        make[TestDependency0].from[TestImpl0].exposed
      })
      .withLocatorPrivacy(LocatorPrivacy.PrivateByDefault)

    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestDependency0].nonEmpty)
    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].nonEmpty)
  }

  "Support private bindings in public-roots mode" in {
    val def1 = PlannerInput
      .target[TestCaseClass2](
        new ModuleDef {
          make[TestInstanceBinding].fromValue(TestInstanceBinding())
          make[TestCaseClass2]
        },
        Activation.empty,
      )
      .withLocatorPrivacy(LocatorPrivacy.PublicRoots)

    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestCaseClass2].nonEmpty)
    assert(loc1.find[TestInstanceBinding].nonEmpty)

    assert(loc2.find[TestCaseClass2].nonEmpty)
    assert(loc2.find[TestInstanceBinding].isEmpty)
  }

  private def prepareInheritedLocator(def1: PlannerInput) = {
    val injector = mkInjector()

    val plan1 = injector.planUnsafe(def1)
    val loc = injector.produce(plan1).unsafeGet()

    val injector2 = Injector.inherit(loc)

    val def2 = PlannerInput.everything(new ModuleDef {
      make[JustTrait].from[Impl0]
    })

    val plan2 = injector2.planUnsafe(def2)
    val loc2 = injector2.produce(plan2).unsafeGet()
    (loc, loc2)
  }

}
