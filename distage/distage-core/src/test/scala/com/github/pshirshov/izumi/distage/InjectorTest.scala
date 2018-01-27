package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.definition.TrivialDIDef
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ContextDefinition}
import com.github.pshirshov.izumi.distage.model.exceptions.{MissingInstanceException, TraitInitializationFailedException, UnsupportedWiringException, UntranslatablePlanException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.model.plan.PlanningFailure.{DuplicatedStatements, UnsolvableConflict}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.DIKey
import com.github.pshirshov.izumi.distage.model.plan.Wiring.UnaryWiring
import com.github.pshirshov.izumi.fundamentals.reflection.EqualitySafeType
import org.scalatest.WordSpec



class InjectorTest extends WordSpec {


  def mkInjector(): Injector = Injector.emerge()

  "DI planner" should {

    "maintain correct operation order" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .binding[TestClass]
        .binding[TestDependency3]
        .binding[TestDependency0, TestImpl0]
        .binding[TestDependency1]
        .binding[TestCaseClass]
        .instance(TestInstanceBinding())
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      assert(plan.steps.exists(_.isInstanceOf[ImportDependency]))

      intercept[MissingInstanceException] {
        injector.produce(plan)
      }

      val fixedPlan = plan.flatMap {
        case ImportDependency(key, _) if key == DIKey.get[NotInContext] =>
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
        .set[JustTrait]
          .named("named.empty.set")
        .set[JustTrait]
        .element[JustTrait, Impl0]
        .element[JustTrait](new Impl1)
        .element[JustTrait](new Impl2())
          .named("named.set")
        .element[JustTrait, Impl3]
          .named("named.set")
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[Set[JustTrait]]("named.set").size == 2)
    }

    "support named bindings" in {
      import Case1_1._
      val definition: ContextDefinition = TrivialDIDef
        .binding[TestClass]
          .named("named.test.class")
        .binding[TestDependency0, TestImpl0]
          .named("named.test.dependency.0")
        .instance(TestInstanceBinding())
          .named("named.test")
        .finish
      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      println(context.get[TestClass]("named.test.class"))
    }


    "support circular dependencies" in {
      import Case2._

      val definition: ContextDefinition = TrivialDIDef
        .binding[Circular2]
        .binding[Circular1]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
    }

    "support complex circular dependencies" in {
      import Case3._

      val definition: ContextDefinition = TrivialDIDef
        .binding[Circular3]
        .binding[Circular1]
        .binding[Circular2]
        .binding[Circular5]
        .binding[Circular4]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      val c3 = context.get[Circular3]
      val traitArg = c3.arg
      assert(traitArg != null && traitArg.isInstanceOf[Circular4])
      assert(c3.method == 2L)
      assert(traitArg.testVal == 1)
      context.enumerate.foreach(println)
    }

    "support trait initialization" in {
      import Case3._

      val definition: ContextDefinition = TrivialDIDef
        .binding[CircularBad1]
        .binding[CircularBad2]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val exc = intercept[TraitInitializationFailedException] {
        injector.produce(plan)
      }
      assert(exc.getCause.isInstanceOf[RuntimeException])

    }

    "support trait fields" in {
      val definition: ContextDefinition = TrivialDIDef
        .binding[Case9.ATraitWithAField]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      assert(context.get[Case9.ATraitWithAField].field == 1)
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
        .binding[Factory]
        .binding[Dependency]
        .binding[OverridingFactory]
        .binding[AssistedFactory]
        .binding[AbstractFactory]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val factory = context.get[Factory]
      assert(factory.wiringTargetForDependency != null)
      assert(factory.factoryMethodForDependency() != factory.wiringTargetForDependency)
      assert(factory.x().b.isInstanceOf[Dependency])

      val abstractFactory = context.get[AbstractFactory]
      assert(abstractFactory.x().isInstanceOf[AbstractDependencyImpl])

      val overridingFactory = context.get[OverridingFactory]
      assert(overridingFactory.x(ConcreteDep()).b.isInstanceOf[ConcreteDep])

      val assistedFactory = context.get[AssistedFactory]
      assert(assistedFactory.x(1).a == 1)
      assert(assistedFactory.x(1).b.isInstanceOf[Dependency])
    }

    // BasicProvisionerTest
    "instantiate simple class" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
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
        .provider[TestClass]((a: Dependency1) => new TestClass(null) )
        .provider[Dependency1](() => new Dependency1Sub {})
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      println(context.parent.get.plan)
      val instantiated = context.get[TestClass]

      println(s"Got instance: ${instantiated.toString}!")
    }

    "handle one-arg trait" in {
      import Case7._

      val definition = TrivialDIDef
        .binding[Dependency1]
        .magic[TestTrait]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]
      assert(instantiated.isInstanceOf[TestTrait])

      println(s"Got instance: ${instantiated.toString}!")
    }

    "handle named one-arg trait" in {
      import Case7._

      val definition = TrivialDIDef
        .binding[Dependency1]
        .magic[TestTrait]
          .named("named-trait")
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]("named-trait")
      assert(instantiated.isInstanceOf[TestTrait])

      println(s"Got instance: ${instantiated.toString}!")
    }

    "handle mixed sub-trait with protected autowires" in {
      import Case8._

      val definition = TrivialDIDef
        .magic[Trait3]
        .magic[Trait2]
        .magic[Trait1]
        .binding[Dependency3]
        .binding[Dependency2]
        .binding[Dependency1]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated1 = context.get[Trait1]
      assert(instantiated1.isInstanceOf[Trait1])

      val instantiated2 = context.get[Trait2]
      assert(instantiated2.isInstanceOf[Trait2])

      val instantiated3 = context.get[Trait3]
      assert(instantiated3.isInstanceOf[Trait3])

      instantiated3.prr()
    }

    "handle sub-type trait" in {
      import Case8._

      val definition = TrivialDIDef
        .magic[Trait2, Trait3]
        .binding[Dependency3]
        .binding[Dependency2]
        .binding[Dependency1]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated3 = context.get[Trait2]
      assert(instantiated3.isInstanceOf[Trait2])

      println(s"Got instance: ${instantiated3.toString}!")
      println(s"Got ${instantiated3.dep1}")
      instantiated3.asInstanceOf[Trait3].prr()
    }
  }

}
