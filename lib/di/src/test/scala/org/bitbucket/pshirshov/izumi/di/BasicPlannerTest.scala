package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.{DIDef, Def}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.exceptions.UntranslatablePlanException
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

  import org.bitbucket.pshirshov.izumi.di.definition.BasicBindingDsl._


  def mkInjector(): Injector = Injector.emerge()


  "DI Context" should {
    "support cute api calls :3" in {
      val context = DefaultBootstrapContext.instance
      assert(context.get[PlanResolver].isInstanceOf[PlanResolverDefaultImpl])
    }
  }

  "DI planner" should {
    "maintain correct operation order" in {
      import Case1._
      val definition: DIDef = new DIDef {
        override def bindings: Seq[Def] = start
          .add[TestClass]
          .add[TestDependency3]
          .add[TestDependency0, TestImpl0]
          .add[TestDependency1]
          .add[TestCaseClass]
          .add(new TestInstanceBinding())
          .finish
      }
      val injector = mkInjector()
      val plan = injector.plan(definition)


      //        val context = injector.produce(plan)
      //        val instance = context.instance[TestClass](DIKey.TypeKey(typeTag[TestClass].tpe.typeSymbol))
      //
      //        System.err.println(s"${instance}")
    }


    "support circular dependencies" in {
      import Case2._

      val definition: DIDef = new DIDef {
        override def bindings: Seq[Def] = start
          .add[Circular2]
          .add[Circular1]
          .finish
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
    }

    "support complex circular dependencies" in {
      import Case3._

      val definition: DIDef = new DIDef {
        override def bindings: Seq[Def] = start
          .add[Circular3]
          .add[Circular1]
          .add[Circular2]
          .finish
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
    }

    "fail on unbindable" in {
      import Case4._

      val definition: DIDef = new DIDef {

        import Def._

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

      val definition: DIDef = new DIDef {
        override def bindings: Seq[Def] = start
          .add[Dependency, Impl1]
          .add[Dependency, Impl2]
          .finish
      }

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[UnsolvableConflict]))
    }

    "handle exactly the same ops" in {
      import Case4._

      val definition: DIDef = new DIDef {
        override def bindings: Seq[Def] = start
          .add[Dependency, Impl1]
          .add[Dependency, Impl1]
          .finish
      }

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[DuplicatedStatement]))

    }

    "handle factory injections" in {
      import Case5._

      val definition: DIDef = new DIDef {
        override def bindings: Seq[Def] = start
          .add[Factory]
          .add[OverridingFactory]
          .add[AssistedFactory]
          .finish
      }

      val injector = mkInjector()
      injector.plan(definition)
    }
  }


}