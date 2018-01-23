package org.bitbucket.pshirshov.izumi.distage

import org.bitbucket.pshirshov.izumi.distage.definition.{ContextDefinition, TrivialDIDef}
import org.scalatest.WordSpec


class DSLTest extends WordSpec {


  "Basic DSL" should {
    "allow to define contexts" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .binding[TestClass]
        .binding[TestDependency0, TestImpl0]
        .instance(new TestInstanceBinding())

        .binding[TestClass]
          .named("named.test.class")
        .binding[TestDependency0]
          .named("named.test.dependency.0")
        .instance(TestInstanceBinding())
          .named("named.test")
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

      assert(definition != null)
    }
  }


}
