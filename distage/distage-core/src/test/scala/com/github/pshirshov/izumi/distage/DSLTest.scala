package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.definition.{Bindings, SimpleModuleDef}
import distage._
import org.scalatest.WordSpec

class DSLTest extends WordSpec {

  "Basic DSL" should {
    "allow to define contexts" in {
      import BasicCase1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass]
        make[TestDependency0].from[TestImpl0]
        make[TestInstanceBinding].from(TestInstanceBinding())

        make[TestClass]
          .named("named.test.class")
        make[TestDependency0]
          .named("named.test.dependency.0")
        make[TestInstanceBinding].named("named.test")
          .from(TestInstanceBinding())
        many[JustTrait].named("named.empty.set")
        many[JustTrait]
          .add[Impl0]
          .add(new Impl1)
        many[JustTrait].named("named.set")
          .add(new Impl2())
        many[JustTrait].named("named.set")
          .add[Impl3]
      }

      assert(definition != null)
    }
  }

  "Module DSL" should {
    "allow to define contexts" in {
      import BasicCase1._

      object Module extends ModuleDef {
        make[TestClass]
        make[TestDependency0].from[TestImpl0]
      }

      assert(Module.bindings == Set(
        Bindings.binding[TestClass]
        , Bindings.binding[TestDependency0, TestImpl0]
      ))
    }

    "correctly handle sets" in {
      import SetCase1._

      val definition = new ModuleDef {
        make[Service2]
        make[Service0]
        make[Service1]
        make[Service3]

        many[SetTrait]
          .add[SetImpl1]
          .add[SetImpl2]
          .add[SetImpl3]

        many[SetTrait].named("n1")
          .add[SetImpl1]
          .add[SetImpl2]
          .add[SetImpl3]

        many[SetTrait].named("n2")
          .add[SetImpl1]
          .add[SetImpl2]
          .add[SetImpl3]

        many[SetTrait].named("n3")
          .add[SetImpl1]
          .add[SetImpl2]
          .add[SetImpl3]
      }

      assert(definition == SimpleModuleDef(
        Set(
          Bindings.emptySet[SetTrait]
          , Bindings.setElement[SetTrait, SetImpl1]
          , Bindings.setElement[SetTrait, SetImpl2]
          , Bindings.setElement[SetTrait, SetImpl3]

          , Bindings.binding[Service0]
          , Bindings.binding[Service1]
          , Bindings.binding[Service2]
          , Bindings.binding[Service3]

          , Bindings.emptySet[SetTrait].named("n1")
          , Bindings.setElement[SetTrait, SetImpl1].named("n1")
          , Bindings.setElement[SetTrait, SetImpl2].named("n1")
          , Bindings.setElement[SetTrait, SetImpl3].named("n1")

          , Bindings.emptySet[SetTrait].named("n2")
          , Bindings.setElement[SetTrait, SetImpl1].named("n2")
          , Bindings.setElement[SetTrait, SetImpl2].named("n2")
          , Bindings.setElement[SetTrait, SetImpl3].named("n2")

          , Bindings.emptySet[SetTrait].named("n3")
          , Bindings.setElement[SetTrait, SetImpl1].named("n3")
          , Bindings.setElement[SetTrait, SetImpl2].named("n3")
          , Bindings.setElement[SetTrait, SetImpl3].named("n3")
        )
      )
      )
    }

    "allow monoidal operations between different types of binding dsls" in {
      import BasicCase1._

      val mod1: ModuleBase = new ModuleDef {
        make[TestClass]
      }

      val mod2: ModuleBase = new ModuleDef {
        make[TestCaseClass2]
      }

      val mod3_1 = new ModuleDef {
        make[TestDependency1]
      }

      val mod3_2 = SimpleModuleDef.empty

      val mod3 = (mod3_1 ++ mod3_2) :+ Bindings.binding[NotInContext]

      val mod4: ModuleBase = SimpleModuleDef {
        Set(
          Bindings.binding(TestInstanceBinding())
        )
      }

      val mod5: ModuleBase = (SimpleModuleDef.empty
        :+ Bindings.binding[TestDependency0, TestImpl0]
        )

      val combinedModules = Seq(mod1, mod2, mod3, mod4, mod5)
        .foldLeft[ModuleBase](SimpleModuleDef.empty)(_ ++ _)

      val complexModule = SimpleModuleDef(Set(
        Bindings.binding[TestClass]
        , Bindings.binding[TestDependency0, TestImpl0]
        , Bindings.binding[TestCaseClass2]
        , Bindings.binding(TestInstanceBinding())
      ))
        .++(mod3) // function pointer equality on magic trait providers

      assert(combinedModules == complexModule)
    }

    "support allTags in module def" in {
      import BasicCase1._

      val definition: ModuleBase = new ModuleDef {
        tag("tag1")
        make[TestClass]
        make[TestDependency0].tagged("sniv")
        tag("tag2")
      }

      assert(definition.bindings == Set(
        Bindings.binding[TestClass].withTags("tag1", "tag2")
        , Bindings.binding[TestDependency0].withTags("tag1", "tag2", "sniv"))
      )
    }
  }

}
