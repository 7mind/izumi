package izumi.distage.injector

import distage._
import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import org.scalatest.wordspec.AnyWordSpec

class PlanOperationsTest extends AnyWordSpec with MkInjector {

  import PlanOperationsTest._

  private val icKey: DIKey = DIKey.get[IntegrationComponent]
  private val pcKey: DIKey = DIKey.get[PrimaryComponent]

  private val sc0: DIKey = DIKey.get[SharedComponent0]
  private val sc1: DIKey = DIKey.get[SharedComponent1]
  private val sc2: DIKey = DIKey.get[SharedComponent2]

  private val injector = mkInjector()

  "support plan trisplit" in {
    val primary = Set(pcKey)
    val sub = Set(icKey)

    val definition = PlannerInput(
      new ModuleDef {
        make[PrimaryComponent]
        make[IntegrationComponent]
        make[SharedComponent0]
        make[SharedComponent1]
        make[SharedComponent2]
      },
      Activation.empty,
      primary ++ sub,
    )

    val split = injector.trisectByKeys(Activation.empty, definition.bindings, primary) {
      baseplan =>
        assert(sub.intersect(baseplan.index.keySet).isEmpty)
        (sub, Set.empty)
    }

    assert(Set(sc0, sc1, sc2).diff(split.shared.index.keySet).isEmpty)

    assert((primary ++ sub).intersect(split.shared.index.keySet).isEmpty)
    assert(primary.intersect(split.side.index.keySet).isEmpty)
    assert(sub.intersect(split.primary.index.keySet).isEmpty)

    assert(split.primary.index.keySet.intersect(split.side.index.keySet) == Set(sc2))
    assert(split.primary.index.keySet.intersect(split.shared.index.keySet) == Set(sc2))
    assert(split.side.index.keySet.intersect(split.shared.index.keySet) == Set(sc2))
  }

  "support ghost components in trisplit" in {
    val primary = Set(pcKey, icKey)
    val sub = Set(icKey)

    val definition = PlannerInput(
      new ModuleDef {
        make[PrimaryComponent]
        make[IntegrationComponent]
        make[SharedComponent0]
        make[SharedComponent1]
        make[SharedComponent2]
      },
      Activation.empty,
      primary ++ sub,
    )

    val split = injector.trisectByKeys(Activation.empty, definition.bindings, primary)(_ => (sub, Set.empty))

    val sideIndex = split.side.index
    val primaryIndex = split.primary.index
    val sharedIndex = split.shared.index

    assert(primaryIndex.keySet.intersect(sideIndex.keySet).intersect(sharedIndex.keySet) == Set(icKey))

    assert(sharedIndex.keySet == Set(sc0, sc1, sc2, icKey))

    assert(sideIndex.keySet == Set(icKey))
    assert(sideIndex.get(icKey).exists(_.isInstanceOf[ImportDependency]))

    assert(primaryIndex.keySet == Set(icKey, pcKey, sc2))
    assert(primaryIndex.get(icKey).exists(_.isInstanceOf[ImportDependency]))
    assert(primaryIndex.get(sc2).exists(_.isInstanceOf[ImportDependency]))
  }

  "support plan separation" in {
    val primary = Set(pcKey)
    val sub = Set(icKey)

    val definition = PlannerInput(
      new ModuleDef {
        make[PrimaryComponent]
        make[IntegrationComponent]
        make[SharedComponent0]
        make[SharedComponent1]
        make[SharedComponent2]
      },
      Activation.empty,
      primary ++ sub,
    )

    val srcPlan = injector.plan(definition)

    def verifySingleImport(key: DIKey): Unit = {
      val plan = srcPlan.replaceWithImports(Set(key))
      assert(plan.index.get(key).exists(_.isInstanceOf[ImportDependency]))
      assert(plan.index.values.collect { case i: ImportDependency => i }.size == 1)
      assert(!plan.definition.keys.contains(key))
      ()
    }

    verifySingleImport(icKey)
    verifySingleImport(sc0)
    verifySingleImport(sc1)
  }
}

object PlanOperationsTest {

  case class SharedComponent0()

  case class SharedComponent1(com0: SharedComponent0)

  case class SharedComponent2(com1: SharedComponent1)

  case class IntegrationComponent(sharedComponent1: SharedComponent2)

  case class PrimaryComponent(sharedComponent1: SharedComponent2)

}
