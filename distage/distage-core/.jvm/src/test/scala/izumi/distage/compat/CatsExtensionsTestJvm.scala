package izumi.distage.compat

import cats.Id
import cats.effect._
import cats.instances.option._
import distage.{DIKey, Injector}
import izumi.distage.InjectorFactory
import izumi.distage.fixtures.BasicCases._
import izumi.distage.fixtures.CircularCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.plan.{ExecutableOp, OrderedPlan, SemiPlan}
import izumi.distage.model.plan.ExecutableOp.WiringOp.UseInstance
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.recursive.Bootloader
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

final class CatsExtensionsTestJvm extends AnyWordSpec with GivenWhenThen {

  "cats-effect extensions" should {
    "work (must rewrite without `Injector.finish` method)" ignore {
      import BasicCase1._
      import CircularCase3._

      val definition1 = PlannerInput.noGC(new ModuleDef {
        make[SelfReference]
      })

      val injector = Injector[IO]()
      val plan = injector.plan(definition1)

      Then("imports should be empty")
      val plan1 = plan.resolveImportsF[Id] {
        case i => throw new RuntimeException(s"Unexpected import: $i")
      }

      assert(plan1 === plan)

      Then("traverse should substitute")
      final case class TestDependency1Eq(unresolved: NotInContext) extends TestDependency1
      val testDependencyPlan = injector.plan(
        PlannerInput.noGC(new ModuleDef {
          make[TestDependency1].from(TestDependency1Eq(_: NotInContext): TestDependency1)
        })
      )
      val dynamicKeys = Set[DIKey](DIKey.get[PlannerInput], DIKey.get[Bootloader], DIKey.get[InjectorFactory])
      def filterDynamic(steps: Seq[ExecutableOp]) = {
        steps.filterNot(b => dynamicKeys.contains(b.target))
      }

      val testDependencyOp = filterDynamic(testDependencyPlan.steps).last.asInstanceOf[SemiplanOp]

      val traversed = testDependencyPlan.traverse[Id] {
        case op if op.target == DIKey.get[NotInContext] => testDependencyOp
        case o => o
      }
//      val plan2 = injector.finish(traversed)
      val plan2: OrderedPlan = (??? : Id[SemiPlan] => OrderedPlan)(traversed)

      assert(filterDynamic(plan2.steps) === filterDynamic(testDependencyPlan.steps))

      case object NotInContext extends NotInContext
      Then("resolveImportsF should work")
      val plan3 = plan2.resolveImportsF[Id] {
        case ImportDependency(target, _, _) if target == DIKey.get[NotInContext] => NotInContext
      }
      assert(plan3.steps.collectFirst { case UseInstance(key, instance, _) if key == DIKey.get[NotInContext] => instance.instance }.contains(NotInContext))

      Then("resolveImport should work")
      val plan4 = plan2.resolveImportF(Option[NotInContext](NotInContext)).get
      assert(plan4.steps.collectFirst { case UseInstance(key, instance, _) if key == DIKey.get[NotInContext] => instance.instance }.contains(NotInContext))

      Then("object graph is correct")
      plan3.render()
      assert(filterDynamic(plan3.steps) == filterDynamic(plan4.steps))

      val objs = injector.produce(plan3).unsafeGet().unsafeRunSync()

      assert(objs.get[TestDependency1].unresolved != null)
      assert(!objs.instances.map(_.value).contains(null))
    }
  }

}
