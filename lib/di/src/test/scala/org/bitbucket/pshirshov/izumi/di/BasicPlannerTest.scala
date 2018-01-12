package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.{DIDef, Def, TrivialDIDef}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.exceptions.{MissingInstanceException, UntranslatablePlanException}
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.{DuplicatedStatement, UnbindableBinding, UnsolvableConflict}
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

  class TestInstanceBinding()

  trait JustTrait {}

  class Impl0 extends JustTrait

  class Impl1 extends JustTrait

  class Impl2 extends JustTrait

  class Impl3 extends JustTrait

}

object Case2 {

  class Circular1(arg: Circular2)

  class Circular2(arg: Circular1)

}

object Case3 {

  class Circular1(arg: Circular2)

  class Circular2(arg: Circular3)

  class Circular3(arg: Circular1)

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


class BasicPlannerTest extends WordSpec {


  def mkInjector(): Injector = Injector.emerge()


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
      assert(context.publicLookup[AnyRef](DIKey.get[PlanResolver]).exists(_.value.isInstanceOf[PlanResolverDefaultImpl]))
      assert(context.publicLookup[Long](DIKey.get[PlanResolver]).isEmpty)

    }
  }

  "Basic DSL" should {
    "allow to define contexts" in {
      import Case1._
      val definition: DIDef = TrivialDIDef
        .empty
        .nameless[TestClass]
        .nameless[TestDependency0, TestImpl0]
        .nameless(new TestInstanceBinding())

        .named[TestClass]("named.test.class")
        .named[TestDependency0, TestImpl0]("named.test.dependency.0")
        .named(new TestInstanceBinding(), "named.test")

        .namedEmptySet[JustTrait]("named.empty.set")
        .namelessEmptySet[JustTrait]

        .namelessSet[JustTrait, Impl0]
        .namelessSet[JustTrait](new Impl1)
        .namedSet[JustTrait](new Impl2(), "named.set")
        .namedSet[JustTrait, Impl3]("named.set")

        .finish
    }
  }

  "DI planner" should {

    "maintain correct operation order" in {
      import Case1._
      val definition: DIDef = TrivialDIDef
        .empty
        .nameless[TestClass]
        .nameless[TestDependency3]
        .nameless[TestDependency0, TestImpl0]
        .nameless[TestDependency1]
        .nameless[TestCaseClass]
        .nameless(new TestInstanceBinding())
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)


      //        val context = injector.produce(plan)
      //        val instance = context.instance[TestClass](DIKey.TypeKey(typeTag[TestClass].tpe.typeSymbol))
      //
      //        System.err.println(s"${instance}")
    }

    "support multiple bindings" in {
      import Case1._
      val definition: DIDef = TrivialDIDef
        .empty
        .namedEmptySet[JustTrait]("named.empty.set")
        .namelessEmptySet[JustTrait]

        .namelessSet[JustTrait, Impl0]
        .namelessSet[JustTrait](new Impl1)
        .namedSet[JustTrait](new Impl2(), "named.set")
        .namedSet[JustTrait, Impl3]("named.set")

        .finish
      val injector = mkInjector()
      val plan = injector.plan(definition)

    }

    "support named bindings" in {
      import Case1._
      val definition: DIDef = TrivialDIDef
        .empty
        .named[TestClass]("named.test.class")
        .named[TestDependency0, TestImpl0]("named.test.dependency.0")
        .named(new TestInstanceBinding(), "named.test")
        .finish
      val injector = mkInjector()
      val plan = injector.plan(definition)

    }


    "support circular dependencies" in {
      import Case2._

      val definition: DIDef = TrivialDIDef
        .empty
        .nameless[Circular2]
        .nameless[Circular1]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
    }

    "support complex circular dependencies" in {
      import Case3._

      val definition: DIDef = TrivialDIDef
        .empty
        .nameless[Circular3]
        .nameless[Circular1]
        .nameless[Circular2]
        .finish

      val injector = mkInjector()
      val plan = injector.plan(definition)
    }

    "fail on unbindable" in {
      import Case4._

      val definition: DIDef = new DIDef {

        import Def._
        import TrivialDIDef._

        override def bindings: Seq[Def] = Seq(
          SingletonBinding(DIKey.get[Dependency], symbolDef[Long])
        )
      }

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[UnbindableBinding]))
    }

    "fail on unsolvable conflicts" in {
      import Case4._

      val definition: DIDef = TrivialDIDef
        .empty
        .nameless[Dependency, Impl1]
        .nameless[Dependency, Impl2]
        .finish

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[UnsolvableConflict]))
    }

    "handle exactly the same ops" in {
      import Case4._

      val definition: DIDef = TrivialDIDef
        .empty
        .nameless[Dependency, Impl1]
        .nameless[Dependency, Impl1]
        .finish

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[DuplicatedStatement]))

    }

    "handle factory injections" in {
      import Case5._

      val definition: DIDef = TrivialDIDef
        .empty
        .nameless[Factory]
        .nameless[OverridingFactory]
        .nameless[AssistedFactory]
        .finish

      val injector = mkInjector()
      injector.plan(definition)
    }
  }


}