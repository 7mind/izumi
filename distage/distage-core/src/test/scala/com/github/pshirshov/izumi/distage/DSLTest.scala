package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.fixtures.BasicCases._
import com.github.pshirshov.izumi.distage.fixtures.SetCases._
import com.github.pshirshov.izumi.distage.model.definition.{Bindings, Module}
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

        make[TestDependency0].namedByImpl.from[TestImpl0]
        make[TestDependency0].namedByImpl
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

      assert(definition == Module.make(
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

      val mod3_2 = Module.empty

      val mod3 = (mod3_1 ++ mod3_2) :+ Bindings.binding[NotInContext]

      val mod4: ModuleBase = Module.make {
        Set(
          Bindings.binding(TestInstanceBinding())
        )
      }

      val mod5: ModuleBase = (Module.empty
        :+ Bindings.binding[TestDependency0, TestImpl0]
        )

      val combinedModules = Seq(mod1, mod2, mod3, mod4, mod5)
        .foldLeft[ModuleBase](Module.empty)(_ ++ _)

      val complexModule = Module.make(Set(
        Bindings.binding[TestClass]
        , Bindings.binding[TestDependency0, TestImpl0]
        , Bindings.binding[TestCaseClass2]
        , Bindings.binding(TestInstanceBinding())
      ))
        .++(mod3) // function pointer equality on magic trait providers

      assert(combinedModules == complexModule)
    }

    "allow operations between objects of ModuleDef" in {
      import BasicCase1._

      object mod1 extends ModuleDef {
        make[TestClass]
      }
      object mod2 extends ModuleDef {
        make[TestDependency0]
      }

      mod1 ++ mod2
    }

    "allow operations between subclasses of ModuleDef" in {
      import BasicCase1._

      class mod1 extends ModuleDef {
        make[TestClass]
      }
      class mod2 extends ModuleDef {
        make[TestDependency0]
      }

      val _: Module = new mod1 ++ new mod2
    }

    "support allTags" in {
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

    "ModuleBuilder supports tags; same bindings with different tags are merged" in {
      import SetCase1._

      val definition = new ModuleDef {
        many[SetTrait].named("n1").tagged("A", "B")
          .add[SetImpl1].tagged("A")
          .add[SetImpl2].tagged("B")
          .add[SetImpl3].tagged("A") // merge
          .add[SetImpl3].tagged("B") // merge

        make[Service1].tagged("CA").from[Service1] // merge
        make[Service1].tagged("CB").from[Service1] // merge

        make[Service2].tagged("CC")

        many[SetTrait].tagged("A", "B")
      }

      assert(definition.bindings.size == 7)
      assert(definition.bindings.count(_.tags == Set("A", "B")) == 3)
      assert(definition.bindings.count(_.tags == Set("CA", "CB")) == 1)
      assert(definition.bindings.count(_.tags == Set("CC")) == 1)
      assert(definition.bindings.count(_.tags == Set("A")) == 1)
      assert(definition.bindings.count(_.tags == Set("B")) == 1)
    }

    "Tags in different modules are merged" in {
      import BasicCase1._

      val def1 = new ModuleDef {
        make[TestDependency0].tagged("a")
        make[TestDependency0].tagged("b")

        tag("1")
      }

      val def2 = new ModuleDef {
        tag("2")

        make[TestDependency0].tagged("x").tagged("y")
      }

      val definition = def1 ++ def2

      assert(definition.bindings.head.tags == Set("1", "2", "a", "b", "x", "y"))
    }

    "Tags in different overriden modules are merged" in {
      import BasicCase1._

      val def1 = new ModuleDef {
        make[TestDependency0].tagged("a").tagged("b")

        tag("1")
      }

      val def2 = new ModuleDef {
        tag("2")

        make[TestDependency0].tagged("x").tagged("y")
      }

      val definition = def1 overridenBy def2

      assert(definition.bindings.head.tags == Set("1", "2", "a", "b", "x", "y"))
    }

    "support includes" in {
      import BasicCase1._

      trait Def1 extends ModuleDef {
        make[TestDependency0]
        tag("tag2")
      }

      trait Def2 extends ModuleDef {
        make[TestClass]
        tag("tag1")
      }

      val definition1 = new Def2 {
        include(new Def1 {})
      }

      val definition2 = new Def1 with Def2

      assert(definition1.bindings.map(_.tags) == Set(Set("tag1"), Set("tag2")))
      assert(definition2.bindings.map(_.tags) == Set(Set("tag1", "tag2")))
    }
  }

}
