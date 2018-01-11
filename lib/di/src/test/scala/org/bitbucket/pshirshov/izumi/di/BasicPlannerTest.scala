package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.{DIDef, Def, ImplDef}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.exceptions.UntranslatablePlanException
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.{DuplicatedStatement, UnbindableBinding, UnsolvableConflict}
import org.bitbucket.pshirshov.izumi.di.planning.{PlanResolver, PlanResolverDefaultImpl}
import org.scalatest.WordSpec

import scala.reflect.runtime.universe._

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

  def symbol[T: Tag]: ImplDef = ImplDef.TypeImpl(typeTag[T].tpe.typeSymbol)

  def mkInjector(): Injector = Injector.make()

  "DI Context" should {
    "support cute api calls :3" in {
      val context = DefaultBootstrapContext
      assert(context.get[PlanResolver].isInstanceOf[PlanResolverDefaultImpl])
    }
  }

  "DI planner" should {
    "maintain correct operation order" in {
      import Case1._
      val definition: DIDef = new DIDef {

        import Def._

        override def bindings: Seq[Def] = Seq(
          SingletonBinding(DIKey.get[TestClass], symbol[TestClass])
          , SingletonBinding(DIKey.get[TestDependency3], symbol[TestDependency3])
          , SingletonBinding(DIKey.get[TestDependency0], symbol[TestImpl0])
          , SingletonBinding(DIKey.get[TestDependency1], symbol[TestDependency1])
          , SingletonBinding(DIKey.get[TestCaseClass], symbol[TestCaseClass])
        )
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

        import Def._

        override def bindings: Seq[Def] = Seq(
          SingletonBinding(DIKey.get[Circular2], symbol[Circular2])
          , SingletonBinding(DIKey.get[Circular1], symbol[Circular1])
        )
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
    }

    "support complex circular dependencies" in {
      import Case3._

      val definition: DIDef = new DIDef {

        import Def._

        override def bindings: Seq[Def] = Seq(
          SingletonBinding(DIKey.get[Circular3], symbol[Circular3])
          , SingletonBinding(DIKey.get[Circular1], symbol[Circular1])
          , SingletonBinding(DIKey.get[Circular2], symbol[Circular2])
        )
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
    }

    "fail on unbindable" in {
      import Case4._

      val definition: DIDef = new DIDef {

        import Def._

        override def bindings: Seq[Def] = Seq(
          SingletonBinding(DIKey.get[Dependency], symbol[Long])
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

        import Def._

        override def bindings: Seq[Def] = Seq(
          SingletonBinding(DIKey.get[Dependency], symbol[Impl1])
          , SingletonBinding(DIKey.get[Dependency], symbol[Impl2])
        )
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

        import Def._

        override def bindings: Seq[Def] = Seq(
          SingletonBinding(DIKey.get[Dependency], symbol[Impl1])
          , SingletonBinding(DIKey.get[Dependency], symbol[Impl1])
        )
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

        import Def._

        override def bindings: Seq[Def] = Seq(
          SingletonBinding(DIKey.get[Factory], symbol[Factory])
          , SingletonBinding(DIKey.get[OverridingFactory], symbol[OverridingFactory])
          , SingletonBinding(DIKey.get[AssistedFactory], symbol[AssistedFactory])
        )
      }

      val injector = mkInjector()
      injector.plan(definition)
    }
  }


}