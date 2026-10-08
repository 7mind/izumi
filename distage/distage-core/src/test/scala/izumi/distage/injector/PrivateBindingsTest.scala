package izumi.distage.injector

import distage.{DIKey, Injector, LocatorPrivacy, ModuleDef, Roots}
import izumi.distage.fixtures.BasicCases.BasicCase1.*
import izumi.distage.model.PlannerInput
import izumi.distage.testkit.runner.spec.AnyWordSpec

class PrivateBindingsTest extends AnyWordSpec with MkInjector {
  "Support private bindings in public-by-default mode" in {
    val def1 = PlannerInput(
      new ModuleDef {
        make[TestDependency0].from[TestImpl0].confined
      },
      Roots(DIKey.get[TestDependency0]),
    )
      .withLocatorPrivacy(LocatorPrivacy.PublicByDefault)

    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestDependency0].nonEmpty)
    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].isEmpty)
  }

  "Support public bindings in public-by-default mode" in {
    val def1 = PlannerInput(
      new ModuleDef {
        make[TestDependency0].from[TestImpl0]
      },
      Roots(DIKey.get[TestDependency0]),
    )
      .withLocatorPrivacy(LocatorPrivacy.PublicByDefault)
    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestDependency0].nonEmpty)
    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].nonEmpty)
  }

  "Support private bindings in private-by-default mode" in {
    val def1 = PlannerInput(
      new ModuleDef {
        make[TestDependency0].from[TestImpl0]
      },
      Roots(DIKey.get[TestDependency0]),
    )
      .withLocatorPrivacy(LocatorPrivacy.PrivateByDefault)

    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestDependency0].nonEmpty)
    assert(loc2.find[JustTrait].nonEmpty)
    assert(loc2.find[TestDependency0].isEmpty)
  }

  "Support public bindings in private-by-default mode" in {
    val def1 = PlannerInput(
      new ModuleDef {
        make[TestDependency0].from[TestImpl0].exposed
      },
      Roots(DIKey.get[TestDependency0]),
    )
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
        }
      )
      .withLocatorPrivacy(LocatorPrivacy.PublicRoots)

    val (loc1, loc2) = prepareInheritedLocator(def1)

    assert(loc1.find[TestCaseClass2].nonEmpty)
    assert(loc1.find[TestInstanceBinding].nonEmpty)

    assert(loc2.find[TestCaseClass2].nonEmpty)
    assert(loc2.find[TestInstanceBinding].isEmpty)
  }

  private def prepareInheritedLocator(def1: PlannerInput) = {
    val loc = producePlannedLocator(mkInjector())(def1)

    val injector2 = Injector.inherit(loc)

    val def2 = PlannerInput
      .everything(new ModuleDef {
        make[JustTrait].from[Impl0]
      })
      .withLocatorPrivacy(def1.locatorPrivacy) // we don't need this at the moment, but it may change in the future

    val loc2 = producePlannedLocator(injector2)(def2)
    (loc, loc2)
  }

}
