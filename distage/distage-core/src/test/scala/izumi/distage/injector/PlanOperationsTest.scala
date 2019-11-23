package izumi.distage.injector

import distage._
import izumi.distage.model.PlannerInput
import org.scalatest.WordSpec



class PlanOperationsTest extends WordSpec with MkInjector {
  import PlanOperationsTest._

  "maintain correct operation order" in {
    val primary = Set[DIKey](DIKey.get[PrimaryComponent])
    val sub = Set[DIKey](DIKey.get[IntegrationComponent])

    val definition = PlannerInput(new ModuleDef {
      make[PrimaryComponent]
      make[IntegrationComponent]
      make[SharedComponent0]
      make[SharedComponent1]
    }, primary ++ sub)

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val split = injector.triSplitPlan(definition.bindings, primary)(_ => sub)

    println(split.shared.plan.render())
    println("---")
    println(split.side.plan.render())
    println("---")
    println(split.primary.plan.render())
    //assert(plan.steps.exists(_.isInstanceOf[ImportDependency]))



  }

}

object PlanOperationsTest {
  case class SharedComponent0()
  case class SharedComponent1(com0: SharedComponent0)
  case class IntegrationComponent(sharedComponent1: SharedComponent1)
  case class PrimaryComponent(sharedComponent1: SharedComponent1)
}