package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.definition.{ContextDefinition, TrivialDIDef}
import org.scalatest.WordSpec


class DSLTest extends WordSpec {


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


}
