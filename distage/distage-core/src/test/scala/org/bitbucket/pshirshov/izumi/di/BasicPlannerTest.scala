package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.{Binding, ContextDefinition, Id, TrivialDIDef}
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.di.model.exceptions.{MissingInstanceException, UnsupportedWiringException, UntranslatablePlanException}
import org.bitbucket.pshirshov.izumi.di.model.plan.{ExecutableOp, UnaryWiring}
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.ImportDependency
import org.bitbucket.pshirshov.izumi.di.model.plan.PlanningFailure.{DuplicatedStatements, UnsolvableConflict}
import org.bitbucket.pshirshov.izumi.di.planning.{PlanResolver, PlanResolverDefaultImpl}
import org.scalatest.WordSpec

object Case1 {

  trait TestDependency0 {
    def boom(): Int = 1
  }

  class TestImpl0 extends TestDependency0 {

  }

  trait NotInContext {}

  trait TestDependency1 {
    // TODO: plan API to let user provide importDefs
    def unresolved: NotInContext
  }

  trait TestDependency3 {
    def methodDependency: TestDependency0

    def doSomeMagic(): Int = methodDependency.boom()
  }

  class TestClass
  (
    val fieldArgDependency: TestDependency0
    , argDependency: TestDependency1
  ) {
    val x = argDependency
    val y = fieldArgDependency
  }

  case class TestCaseClass(a1: TestClass, a2: TestDependency3)

  case class TestInstanceBinding(z: String =
                                 """R-r-rollin' down the window, white widow, fuck fame
Forest fire, climbin' higher, real life, it can wait""")

  case class TestCaseClass2(a: TestInstanceBinding)

  trait JustTrait {}

  class Impl0 extends JustTrait

  class Impl1 extends JustTrait

  class Impl2 extends JustTrait

  class Impl3 extends JustTrait

}

object Case1_1 {

  trait TestDependency0 {
    def boom(): Int = 1
  }

  class TestClass
  (
    @Id("named.test.dependency.0") val fieldArgDependency: TestDependency0
    , @Id("named.test") argDependency: TestInstanceBinding
  ) {
    val x = argDependency
    val y = fieldArgDependency
  }

  class TestImpl0 extends TestDependency0 {

  }

  case class TestInstanceBinding(z: String =
                                 """R-r-rollin' down the window, white widow, fuck fame
Forest fire, climbin' higher, real life, it can wait""")

}

object Case2 {

  trait Circular1 {
    def arg: Circular2
  }

  class Circular2(arg: Circular1)

}

object Case3 {

  trait Circular1 {
    def arg: Circular2
  }

  trait Circular2 {
    def arg: Circular3
  }

  trait Circular3 {
    def arg: Circular4

    def arg2: Circular5
  }

  trait Circular4 {
    def arg: Circular1
  }

  trait Circular5 {
    def arg: Circular1

    def arg2: Circular4
  }

}

object Case4 {

  trait Dependency

  class Impl1 extends Dependency

  class Impl2 extends Dependency

}


object Case5 {

  trait Dependency

  class TestClass(b: Dependency)

  class AssistedTestClass(val a: Int, b: Dependency)

  trait Factory {
    def x(): TestClass
  }

  trait OverridingFactory {
    def x(b: Dependency): TestClass
  }

  trait AssistedFactory {
    def x(a: Int): TestClass
  }

}

object Case6 {

  trait Dependency1

  trait Dependency1Sub extends Dependency1

  class TestClass(b: Dependency1)

  class TestClass2(a: TestClass)

}


class BasicPlannerTest extends WordSpec {


  def mkInjector(): Injector = Injector.emerge()


  "DI Keys" should {
    "support equality checks" in {
      assert(DIKey.get[PlanResolver] == DIKey.get[PlanResolver])
      assert(DIKey.get[PlanResolver].named("xxx") == DIKey.get[PlanResolver].named("xxx"))
    }
  }

  "DI Context" should {
    "support cute api calls :3" in {
      import scala.language.reflectiveCalls
      val context = new DefaultBootstrapContext() {
        def publicLookup[T: Tag](key: DIKey): Option[TypedRef[T]] = super.lookup(key)
      }

      assert(context.find[PlanResolver].exists(_.isInstanceOf[PlanResolverDefaultImpl]))
      assert(context.find[PlanResolver]("another.one").isEmpty)

      assert(context.get[PlanResolver].isInstanceOf[PlanResolverDefaultImpl])
      intercept[MissingInstanceException] {
        context.get[PlanResolver]("another.one")
      }

      assert(context.publicLookup[PlanResolver](DIKey.get[PlanResolver]).exists(_.value.isInstanceOf[PlanResolverDefaultImpl]))
      assert(context.publicLookup[Any](DIKey.get[PlanResolver]).exists(_.value.isInstanceOf[PlanResolverDefaultImpl]))
      assert(context.publicLookup[Long](DIKey.get[PlanResolver]).isEmpty)

    }
  }

  "Basic DSL" should {
    "allow to define contexts" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .empty
        .binding[TestClass]
        .binding[TestDependency0, TestImpl0]
        .instance(new TestInstanceBinding())

        .named("named.test.class").binding[TestClass]
        .named("named.test.dependency.0").binding[TestDependency0]
        .named("named.test").instance(TestInstanceBinding())
        .named("named.empty.set").set[JustTrait]
        .set[JustTrait]

        .element[JustTrait, Impl0]
        .element[JustTrait](new Impl1)
        .named("named.set").element[JustTrait](new Impl2())
        .named("named.set").element[JustTrait, Impl3]

        .finish

      assert(definition != null)
    }
  }

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
