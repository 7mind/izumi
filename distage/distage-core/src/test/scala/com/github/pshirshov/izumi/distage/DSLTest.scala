package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.definition.TrivialDIDef
import com.github.pshirshov.izumi.distage.model.definition.ContextDefinition
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

    "allow to define magic contexts" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .magic[TestClass]
        .magic[TestDependency0, TestImpl0]
        .instance(new TestInstanceBinding())

        .magic[TestClass]
          .named("named.test.class")
        .magic[TestDependency0]
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
