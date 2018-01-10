package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.{DIDef, Def}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.scalatest.WordSpec

import scala.reflect.runtime.universe._

object Case1 {

  trait TestDependency0 {
  }

  class TestImpl0 extends TestDependency0 {

  }

  trait TestDependency1 {
    def unresolved: Long
  }

  trait TestDependency3 {
    def methodDependency: TestDependency0
  }

  class TestClass
  (
    val fieldDependency: TestDependency0
    , argDependency: TestDependency1
  ) {
    val x = argDependency
    val y = fieldDependency
  }

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

class BasicTest extends WordSpec {

  def symbol[T:Tag] = typeTag[T].tpe.typeSymbol

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
        )
      }
      val injector: Injector = new BasicInjector()
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

      val injector: Injector = new BasicInjector()
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

      val injector: Injector = new BasicInjector()
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

      val injector: Injector = new BasicInjector()
      intercept[IllegalStateException] {
        injector.plan(definition)
      }
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

      val injector: Injector = new BasicInjector()
      val plan = injector.plan(definition)
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

      val injector: Injector = new BasicInjector()
      val plan = injector.plan(definition)
    }
  }


}