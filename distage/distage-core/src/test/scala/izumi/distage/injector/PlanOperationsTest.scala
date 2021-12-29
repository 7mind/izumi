package izumi.distage.injector

import distage.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import org.scalatest.wordspec.AnyWordSpec

class PlanOperationsTest extends AnyWordSpec with MkInjector {

  import PlanOperationsTest._

  private val icKey: DIKey = DIKey.get[IntegrationComponent]
  private val pcKey: DIKey = DIKey.get[PrimaryComponent]

  private val sc0: DIKey = DIKey.get[SharedComponent0]
  private val sc1: DIKey = DIKey.get[SharedComponent1]

  private val injector = mkInjector()

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
      assert(plan.plan.meta.nodes.get(key).exists(_.isInstanceOf[ImportDependency]))
      assert(plan.plan.meta.nodes.values.collect { case i: ImportDependency => i }.size == 1)
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
