package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.definition.{Binding, ContextDefinition, TrivialDIDef}
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.{MissingInstanceException, UnsupportedWiringException, UntranslatablePlanException}
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import org.bitbucket.pshirshov.izumi.distage.model.plan.PlanningFailure.{DuplicatedStatements, UnsolvableConflict}
import org.bitbucket.pshirshov.izumi.distage.model.plan.{ExecutableOp, UnaryWiring}
import org.bitbucket.pshirshov.izumi.distage.model.{DIKey, EqualitySafeType}
import org.scalatest.WordSpec



class InjectorTest extends WordSpec {


  def mkInjector(): Injector = Injector.emerge()

  "DI planner" should {

    "maintain correct operation order" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .empty
        .binding[TestClass]
        .binding[TestDependency3]
        .binding[TestDependency0, TestImpl0]
        .binding[TestDependency1]
        .binding[TestCaseClass]
        .instance(new TestInstanceBinding())
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      assert(plan.steps.exists(_.isInstanceOf[ImportDependency]))

      intercept[MissingInstanceException] {
        injector.produce(plan)
      }

      val fixedPlan = plan.flatMap {
        case ImportDependency(key, references) if key == DIKey.get[NotInContext] =>
          Seq(ExecutableOp.WiringOp.ReferenceInstance(
            key
            , UnaryWiring.Instance(EqualitySafeType.get[NotInContext], new NotInContext {})
          ))

        case op =>
          Seq(op)
      }
      injector.produce(fixedPlan)
    }

    "support multiple bindings" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .empty
        .named("named.empty.set").set[JustTrait]
        .set[JustTrait]

        .element[JustTrait, Impl0]
        .element[JustTrait](new Impl1)
        .named("named.set").element[JustTrait](new Impl2())
        .named("named.set").element[JustTrait, Impl3]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

    }

    "support named bindings" in {
      import Case1_1._
      val definition: ContextDefinition = TrivialDIDef
        .empty
        .named("named.test.class").binding[TestClass]
        .named("named.test.dependency.0").binding[TestDependency0, TestImpl0]
        .named("named.test").instance(TestInstanceBinding())
        .finish
      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      println(context.get[TestClass]("named.test.class"))
    }


    "support circular dependencies" in {
      import Case2._

      val definition: ContextDefinition = TrivialDIDef
        .empty
        .binding[Circular2]
        .binding[Circular1]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
    }

    "support complex circular dependencies" in {
      import Case3._

      val definition: ContextDefinition = TrivialDIDef
        .empty
        .binding[Circular3]
        .binding[Circular1]
        .binding[Circular2]
        .binding[Circular5]
        .binding[Circular4]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      println(context.get[Circular3])
      context.enumerate.foreach(println)
    }

    "fail on unbindable" in {
      import Case4._

      val definition: ContextDefinition = new ContextDefinition {

        import Binding._
        import TrivialDIDef._

        override def bindings: Seq[Binding] = Seq(
          SingletonBinding(DIKey.get[Dependency], symbolDef[Long])
        )
      }

      val injector = mkInjector()
      intercept[UnsupportedWiringException] {
        injector.plan(definition)
      }
      //assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[UnbindableBinding]))
    }

    "fail on unsolvable conflicts" in {
      import Case4._

      val definition: ContextDefinition = TrivialDIDef
        .empty
        .binding[Dependency, Impl1]
        .binding[Dependency, Impl2]
        .finish

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[UnsolvableConflict]))
    }

    "handle exactly the same ops" in {
      import Case4._

      val definition: ContextDefinition = TrivialDIDef
        .empty
        .binding[Dependency, Impl1]
        .binding[Dependency, Impl1]
        .finish

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[DuplicatedStatements]))

    }

    "handle factory injections" in {
      import Case5._

      val definition: ContextDefinition = TrivialDIDef
        .empty
        .binding[Factory]
        .binding[Dependency]
        .binding[OverridingFactory]
        .binding[AssistedFactory]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
    }

    // BasicProvisionerTest
    "instantiate simple class" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .empty
        .binding[TestCaseClass2]
        .instance(new TestInstanceBinding)
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      val instantiated = context.get[TestCaseClass2]

      println(s"Got instance: ${instantiated.toString}!")
    }

    "instantiate provider bindings" in {
      import Case6._

      val definition: ContextDefinition = TrivialDIDef
        .empty
        .provider[TestClass]((a: Dependency1) => new TestClass(null))
        .provider[Dependency1](() => new Dependency1Sub {})
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      println(context.parent.get.plan)
      val instantiated = context.get[TestClass]

      println(s"Got instance: ${instantiated.toString}!")
    }
  }


}
