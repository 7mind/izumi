package izumi.distage.testkit.catstest

import cats.Id
import cats.effect._
import cats.instances.option._
import distage.{DIKey, Injector}
import izumi.distage.fixtures.BasicCases._
import izumi.distage.fixtures.CircularCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.plan.ExecutableOp.SemiplanOp
import izumi.distage.model.plan.ExecutableOp.WiringOp.UseInstance
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

class CatsExtensionsTest extends AnyWordSpec with GivenWhenThen {

  "cats-effect extensions" should {
    "work" in {
      import BasicCase1._
      import CircularCase3._

      val definition1 = PlannerInput.noGc(new ModuleDef {
        make[SelfReference]
      })

      val injector = Injector.Standard()
      val plan = injector.plan(definition1)

      Then("imports should be empty")
      val plan1 = plan.resolveImportsF[Id] {
        case _ => throw new RuntimeException()
      }

      assert(plan1 === plan)

      Then("traverse should substitute")
      final case class TestDependency1Eq(unresolved: NotInContext) extends TestDependency1
      val testDependencyPlan = injector.plan(
        PlannerInput.noGc(new ModuleDef {
          make[TestDependency1].from(TestDependency1Eq(_: NotInContext): TestDependency1)
        })
      )
      val testDependencyOp = testDependencyPlan.steps.last.asInstanceOf[SemiplanOp]

      val plan2 = injector.finish(testDependencyPlan.traverse[Id] { _ => testDependencyOp })

      assert(plan2.steps === testDependencyPlan.steps)

      case object NotInContext extends NotInContext
      Then("resolveImportsF should work")
      val plan3 = plan2.resolveImportsF[Id] {
        case _ => NotInContext
      }
      assert(plan3.steps.collectFirst { case UseInstance(key, instance, _) if key == DIKey.get[NotInContext] => instance.instance }.contains(NotInContext))

      Then("resolveImport should work")
      val plan4 = plan2.resolveImportF(Option[NotInContext](NotInContext)).get
      assert(plan4.steps.collectFirst { case UseInstance(key, instance, _) if key == DIKey.get[NotInContext] => instance.instance }.contains(NotInContext))

      Then("object graph is correct")
      assert(plan3 == plan4)

      val objs = injector.produceUnsafeF[IO](plan3).unsafeRunSync()

      assert(objs.get[TestDependency1].unresolved != null)
      assert(!objs.instances.map(_.value).contains(null))
    }
  }

}
