package izumi.distage.injector

import distage._
import izumi.distage.model.PlannerInput
import org.scalatest.WordSpec


class PlanOperationsTest extends WordSpec with MkInjector {

  import PlanOperationsTest._

  "support plan trisplit" in {
    val primary = Set[DIKey](DIKey.get[PrimaryComponent])
    val sub = Set[DIKey](DIKey.get[IntegrationComponent])

    val definition = PlannerInput(new ModuleDef {
      make[PrimaryComponent]
      make[IntegrationComponent]
      make[SharedComponent0]
      make[SharedComponent1]
      make[SharedComponent2]
    }, primary ++ sub)

    val injector = mkInjector()

    val split = injector.triSplitPlan(definition.bindings, primary)(_ => sub)

    assert(Set[DIKey](DIKey.get[SharedComponent0], DIKey.get[SharedComponent2]).diff(split.shared.plan.index.keySet).isEmpty)
    assert((primary ++ sub).intersect(split.shared.plan.index.keySet).isEmpty)
    assert(primary.intersect(split.side.plan.index.keySet).isEmpty)
    assert(sub.intersect(split.primary.plan.index.keySet).isEmpty)

//    println(split.shared.plan.render())
//    println("///")
//    println(split.side.plan.render())
//    println("///")
//    println(split.primary.plan.render())

    assert(split.primary.plan.index.keySet.intersect(split.side.plan.index.keySet) == Set[DIKey](DIKey.get[SharedComponent2]))
    assert(split.primary.plan.index.keySet.intersect(split.shared.plan.index.keySet) == Set[DIKey](DIKey.get[SharedComponent2]))
    assert(split.side.plan.index.keySet.intersect(split.shared.plan.index.keySet) == Set[DIKey](DIKey.get[SharedComponent2]))
  }

}

object PlanOperationsTest {

  case class SharedComponent0()

  case class SharedComponent1(com0: SharedComponent0)

  case class SharedComponent2(com1: SharedComponent1)

  case class IntegrationComponent(sharedComponent1: SharedComponent2)

  case class PrimaryComponent(sharedComponent1: SharedComponent2)

}