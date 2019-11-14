package izumi.distage.dsl

import distage._
import izumi.distage.fixtures.BasicCases._
import izumi.distage.fixtures.SetCases._
import izumi.distage.model.definition.{BindingTag, Bindings, ImplDef, Module}
import distage._
import izumi.distage.injector.MkInjector
import izumi.distage.model.definition.Binding.SingletonBinding
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.WordSpec

class DSLTest extends WordSpec with MkInjector {

  import TestTagOps._

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
        Bindings.binding[TestClass].addTags(Set("tag1", "tag2"))
        , Bindings.binding[TestDependency0].addTags(Set("tag1", "tag2", "sniv")))
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
      assert(definition.bindings.count(_.tags.strings == Set("A", "B")) == 3)
      assert(definition.bindings.count(_.tags.strings == Set("CA", "CB")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("CC")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("A")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("B")) == 1)
    }

    "Multiset bindings support tag merge" in {
      import SetCase1._

//      val set1 = Set(new SetImpl4, new SetImpl4)
//          .addSet(set1).tagged("A") // don't merge (function bind)
//          .addSet(set1).tagged("B") // don't merge (function bind)

      val set = Set(new SetImpl5, new SetImpl5)

      val definition = new ModuleDef {
        many[SetTrait].named("n1").tagged("A", "B")
          .addSetValue(set).tagged("A") // merge
          .addSetValue(set).tagged("B") // merge
      }

      assert(definition.bindings.size == 3)
      assert(definition.bindings.count(_.tags.strings == Set("A", "B")) == 2)
    }

    "Set bindings with the same source position but different implementations do not conflict" in {
      val definition: ModuleDef = new ModuleDef {
        def int(int: Int) = many[Int].addValue(int)

        int(1)
        int(2)
        int(3)
      }

      assert(definition.bindings.size == 4)
      assert(definition.bindings.collect {
        case SetElementBinding(_, ImplDef.InstanceImpl(_, n: Int), _, _) => n
      } == Set(1, 2, 3))
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

      assert(definition.bindings.head.tags.strings == Set("1", "2", "a", "b", "x", "y"))
    }

    "Tags in different overriden modules are merged" in {
      import BasicCase1._

      val tags12: Seq[BindingTag] = Seq("1", "2")

      val def1 = new ModuleDef {
        make[TestDependency0].tagged("a").tagged("b")

        tag(tags12: _*)
      }

      val def2 = new ModuleDef {
        tag("2", "3")

        make[TestDependency0].tagged("x").tagged("y")
      }

      val definition = def1 overridenBy def2

      assert(definition.bindings.head.tags.strings == Set("1", "2", "3", "a", "b", "x", "y"))

    }

    "support zero element" in {
      import BasicCase1._
      val def1 = new ModuleDef {
        make[TestDependency0]
      }

      val def2 = new ModuleDef {
        make[TestDependency0]
      }
      val def3 = new ModuleDef {
        make[TestDependency1]
      }

      assert((def1 overridenBy Module.empty) == def1)
      assert(def1 == def2)
      assert(def1 != def3)

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

      assert(definition1.bindings.map(_.tags.strings) == Set(Set("tag1"), Set("tag2")))
      assert(definition2.bindings.map(_.tags.strings) == Set(Set("tag1", "tag2")))
    }

    "support binding to multiple interfaces" in {
      import BasicCase6._

      val implXYZ = new ImplXYZ

      val definition = new ModuleDef {
        bind[ImplXYZ]
          .to[TraitX]
          .to[TraitY]
          .to[TraitZ]
      }

      assert(definition === Module.make(
        Set(
          Bindings.binding[ImplXYZ]
          , Bindings.reference[TraitX, ImplXYZ]
          , Bindings.reference[TraitY, ImplXYZ]
          , Bindings.reference[TraitZ, ImplXYZ]
        )
      ))

      val definitionEffect = new ModuleDef {
        bindEffect[Identity, ImplXYZ](implXYZ).to[TraitX].to[TraitY].to[TraitZ]
      }

      assert(definitionEffect === Module.make(
        Set(
          SingletonBinding(DIKey.get[ImplXYZ], ImplDef.EffectImpl(SafeType.get[ImplXYZ], SafeType.getK[Identity],
            ImplDef.InstanceImpl(SafeType.get[ImplXYZ], implXYZ)))
          , Bindings.reference[TraitX, ImplXYZ]
          , Bindings.reference[TraitY, ImplXYZ]
          , Bindings.reference[TraitZ, ImplXYZ]
        )
      ))

      val definitionResource = new ModuleDef {
        bindResource[DIResource.Simple[ImplXYZ], ImplXYZ].to[TraitX].to[TraitY].to[TraitZ]
      }

      assert(definitionResource === Module.make(
        Set(
          SingletonBinding(DIKey.get[ImplXYZ], ImplDef.ResourceImpl(SafeType.get[ImplXYZ], SafeType.getK[Identity],
            ImplDef.TypeImpl(SafeType.get[DIResource.Simple[ImplXYZ]])))
          , Bindings.reference[TraitX, ImplXYZ]
          , Bindings.reference[TraitY, ImplXYZ]
          , Bindings.reference[TraitZ, ImplXYZ]
        )
      ))

    }

    "support bindings to multiple interfaces (injector test)" in {
      import BasicCase6._

      val definition = PlannerInput.noGc(new ModuleDef {
        bind[ImplXYZ].named("my-impl")
          .to[TraitX]
          .to[TraitY]("Y")
      })

      val defWithoutSugar = PlannerInput.noGc(new ModuleDef {
        make[ImplXYZ].named("my-impl")
        make[TraitX].using[ImplXYZ]("my-impl")
        make[TraitY].named("Y").using[ImplXYZ]("my-impl")
      })

      val injector = mkInjector()
      val plan1 = injector.plan(definition)
      val plan2 = injector.plan(defWithoutSugar)
      assert(plan1.definition == plan2.definition)

      val context = injector.produceUnsafe(plan1)
      assert(context.get[TraitX].isInstanceOf[ImplXYZ])
      assert(context.get[TraitY]("Y").isInstanceOf[ImplXYZ])
    }
  }

}
