package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.definition.{DIDef, Def}
import org.scalatest.WordSpec

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

class BasicTest extends WordSpec {

  "A Set" when {
    "empty" should {
      "have size 0" in {
        val definition: DIDef = new DIDef {

          import Def._

          override def bindings: Seq[Def] = Seq(
            SingletonBinding[TestClass, TestClass]()
            , SingletonBinding[TestDependency3, TestDependency3]()
            , SingletonBinding[TestDependency0, TestImpl0]()
            , SingletonBinding[TestDependency1, TestDependency1]()
          )
        }
        val injector: Injector = new BasicInjector()
        val plan = injector.plan(definition)

        System.err.println("=" * 120)

        System.err.println(s"${plan}")

        //        val context = injector.produce(plan)
        //        val instance = context.instance[TestClass](DIKey.TypeKey(typeTag[TestClass].tpe.typeSymbol))
        //
        //        System.err.println(s"${instance}")
      }

    }
  }
}