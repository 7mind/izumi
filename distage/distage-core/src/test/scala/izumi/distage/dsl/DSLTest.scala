package izumi.distage.dsl

import distage._
import izumi.distage.constructors.ClassConstructor
import izumi.distage.fixtures.BasicCases._
import izumi.distage.fixtures.SetCases._
import izumi.distage.injector.MkInjector
import izumi.distage.model.definition.Binding.{SetElementBinding, SingletonBinding}
import izumi.distage.model.definition.StandardAxis.{Mode, Repo}
import izumi.distage.model.definition.{Binding, BindingTag, Bindings, ImplDef, Lifecycle, Module}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.SourceFilePosition
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class DSLTest extends AnyWordSpec with MkInjector {

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
        make[TestInstanceBinding]
          .named("named.test")
          .from(TestInstanceBinding())
        many[JustTrait].named("named.empty.set")
        many[JustTrait]
          .add[Impl0]
          .add(new Impl1)
        many[JustTrait]
          .named("named.set")
          .add(new Impl2())
        many[JustTrait]
          .named("named.set")
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

      assert(
        Module.bindings == Set(
          Bindings.binding[TestClass],
          Bindings.binding[TestDependency0, TestImpl0],
        )
      )
    }

    "support annotated parameters" in {
      import BasicCase1._

      object Module extends ModuleDef {
        make[TestClass].annotateParameter[TestDependency0]("test_param")
        make[TestDependency0].named("test_param").from[TestImpl0]
      }

      assert(
        Module
          .bindings.find(_.key.tpe == DIKey[TestClass].tpe).exists {
            case Binding.SingletonBinding(_, ImplDef.ProviderImpl(_, function), _, _, _) =>
              function.diKeys.exists {
                case DIKey.IdKey(tpe, id, _) => DIKey[TestDependency0].tpe == tpe && id == "test_param"
                case _ => false
              }
            case _ => false
          }
      )
    }

    "produce modules with annotated parameters" in {
      import BasicCase1._

      object ModuleAnnotated extends ModuleDef {
        make[TestClass].annotateParameter[TestDependency0]("test_param")
        make[TestDependency1].from[TestImpl1]
        make[TestDependency0].from[TestImpl0]
        make[TestDependency0].named("test_param").from[TestImpl0]
      }

      val injector = mkInjector()
      val definitionAnnotated = PlannerInput(ModuleAnnotated, Activation(), Roots.Everything)
      val planAnnotated = injector.plan(definitionAnnotated)

      assert(planAnnotated.definition.bindings.nonEmpty)

      injector.produce(planAnnotated).use {
        locator =>
          val tc = locator.get[TestClass]
          val ta0 = locator.get[TestDependency0]("test_param")
          val t0 = locator.get[TestDependency0]
          assert(tc.fieldArgDependency == ta0)
          assert(tc.fieldArgDependency != t0)
      }
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

      assert(
        definition == Module.make(
          Set(
            Bindings.emptySet[SetTrait],
            Bindings.setElement[SetTrait, SetImpl1],
            Bindings.setElement[SetTrait, SetImpl2],
            Bindings.setElement[SetTrait, SetImpl3],
            Bindings.binding[Service0],
            Bindings.binding[Service1],
            Bindings.binding[Service2],
            Bindings.binding[Service3],
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
        :+ Bindings.binding[TestDependency0, TestImpl0])

      val combinedModules = Seq(mod1, mod2, mod3, mod4, mod5)
        .foldLeft[ModuleBase](Module.empty)(_ ++ _)

      val complexModule = Module
        .make(
          Set(
            Bindings.binding[TestClass],
            Bindings.binding[TestDependency0, TestImpl0],
            Bindings.binding[TestCaseClass2],
            Bindings.binding(TestInstanceBinding()),
          )
        )
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

      assert(definition.bindings == Set(Bindings.binding[TestClass].addTags(Set("tag1", "tag2")), Bindings.binding[TestDependency0].addTags(Set("tag1", "tag2", "sniv"))))
    }

    "ModuleBuilder supports tags; same bindings with different tags are NOT merged (tag merging removed in 0.11.0)" in {
      import SetCase1._

      val definition = new ModuleDef {
        many[SetTrait]
          .named("n1")
          .add[SetImpl1].tagged("A")
          .add[SetImpl2].tagged("B")
          .add[SetImpl3].tagged("A") // merge
          .add[SetImpl3].tagged("B") // merge

        make[Service1].tagged("CA").from[Service1] // merge
        make[Service1].tagged("CB").from[Service1] // merge

        make[Service2].tagged("CC")

        many[SetTrait]
      }

      assert(definition.bindings.size == 9)
      assert(definition.bindings.count(_.tags.strings == Set("A")) == 2)
      assert(definition.bindings.count(_.tags.strings == Set("B")) == 2)
      assert(definition.bindings.count(_.tags.strings == Set("A", "B")) == 0)
      assert(definition.bindings.count(_.tags.strings == Set("CA")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("CB")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("CA", "CB")) == 0)
      assert(definition.bindings.count(_.tags.strings == Set("CC")) == 1)
    }

    "Multiset bindings do NOT support tag merge (tag merging removed in 0.11.0)" in {
      import SetCase1._

      val set = Set(new SetImpl5, new SetImpl5)

      val definition = new ModuleDef {
        many[SetTrait]
          .named("n1")
          .addSetValue(set).tagged("A") // merge
          .addSetValue(set).tagged("B") // merge
      }

      assert(definition.bindings.size == 4)
      assert(definition.bindings.count(_.tags.strings == Set("A")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("B")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("A", "B")) == 0)
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

    "Tags in different modules are NOT merged (tag merging removed in 0.11.0)" in {
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

      assert(definition.bindings.count(_.tags.strings == Set("a", "1")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("b", "1")) == 1)
      assert(definition.bindings.count(_.tags.strings == Set("x", "y", "2")) == 1)
    }

    "Tags in different overriden modules are NOT merged, later definition beets out former and removes its tags (tag merging removed in 0.11.0)" in {
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

      assert(definition.bindings.count(_.tags.strings == Set("a", "b", "1", "2")) == 0)
      assert(definition.bindings.count(_.tags.strings == Set("x", "y", "2", "3")) == 1)
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

      val implXYZ: Identity[ImplXYZ] = new ImplXYZ
      val implXYZResource = Lifecycle.make(implXYZ)(_ => ())

      val definition = new ModuleDef {
        make[ImplXYZ]
          .aliased[TraitX]
          .aliased[TraitY]
          .aliased[TraitZ]
      }

      assert(
        definition == Module.make(
          Set(
            Bindings.binding[ImplXYZ],
            Bindings.reference[TraitX, ImplXYZ],
            Bindings.reference[TraitY, ImplXYZ],
            Bindings.reference[TraitZ, ImplXYZ],
          )
        )
      )

      val definitionEffect = new ModuleDef {
        make[ImplXYZ]
          .fromEffect(implXYZ)
          .aliased[TraitX]
          .aliased[TraitY]
          .aliased[TraitZ]
      }

      assert(
        definitionEffect == Module.make(
          Set(
            SingletonBinding(
              DIKey.get[ImplXYZ],
              ImplDef.EffectImpl(SafeType.get[ImplXYZ], SafeType.getK[Identity], ImplDef.InstanceImpl(SafeType.get[ImplXYZ], implXYZ)),
              Set.empty,
              SourceFilePosition.unknown,
            ),
            Bindings.reference[TraitX, ImplXYZ],
            Bindings.reference[TraitY, ImplXYZ],
            Bindings.reference[TraitZ, ImplXYZ],
          )
        )
      )

      class X extends Lifecycle.Simple[ImplXYZ] {
        override def acquire: ImplXYZ = new ImplXYZ
        override def release(resource: ImplXYZ): Unit = ()
      }

      val definitionResource = new ModuleDef {
        make[ImplXYZ]
          .fromResource[X]
          .aliased[TraitX]
          .aliased[TraitY]
          .aliased[TraitZ]
      }
      val expectedResource = Module.make(
        Set(
          SingletonBinding(
            DIKey.get[ImplXYZ],
            ImplDef.ResourceImpl(
              SafeType.get[ImplXYZ],
              SafeType.getK[Identity],
              ImplDef.ProviderImpl(SafeType.get[X], ClassConstructor[X].get),
            ),
            Set.empty,
            SourceFilePosition.unknown,
          ),
          Bindings.reference[TraitX, ImplXYZ],
          Bindings.reference[TraitY, ImplXYZ],
          Bindings.reference[TraitZ, ImplXYZ],
        )
      )
      assert(definitionResource == expectedResource)

      val definitionResourceFn = new ModuleDef {
        make[ImplXYZ]
          .fromResource(implXYZResource)
          .aliased[TraitX]
          .aliased[TraitY]
          .aliased[TraitZ]
      }

      assert(
        definitionResourceFn == Module.make(
          Set(
            SingletonBinding(
              DIKey.get[ImplXYZ],
              ImplDef.ResourceImpl(SafeType.get[ImplXYZ], SafeType.getK[Identity], ImplDef.InstanceImpl(SafeType.get[Lifecycle[Identity, ImplXYZ]], implXYZResource)),
              Set.empty,
              SourceFilePosition.unknown,
            ),
            Bindings.reference[TraitX, ImplXYZ],
            Bindings.reference[TraitY, ImplXYZ],
            Bindings.reference[TraitZ, ImplXYZ],
          )
        )
      )

    }

    "support bindings to multiple interfaces (injector test)" in {
      import BasicCase6._

      val definition = PlannerInput.noGC(new ModuleDef {
        make[ImplXYZ]
          .named("my-impl")
          .aliased[TraitX]
          .aliased[TraitY]("Y")
      })

      val defWithoutSugar = PlannerInput.noGC(new ModuleDef {
        make[ImplXYZ].named("my-impl")
        make[TraitX].using[ImplXYZ]("my-impl")
        make[TraitY].named("Y").using[ImplXYZ]("my-impl")
      })

      val defWithTags = PlannerInput.noGC(new ModuleDef {
        make[ImplXYZ]
          .named("my-impl").tagged("tag1")
          .aliased[TraitX]
          .aliased[TraitY]
      })

      val defWithTagsWithoutSugar = PlannerInput.noGC(new ModuleDef {
        make[ImplXYZ].named("my-impl").tagged("tag1")
        make[TraitX].tagged("tag1").using[ImplXYZ]("my-impl")
        make[TraitY].tagged("tag1").using[ImplXYZ]("my-impl")
      })

      val injector = mkInjector()
      val plan1 = injector.plan(definition)
      val plan2 = injector.plan(defWithoutSugar)
      assert(plan1.definition == plan2.definition)

      val plan3 = injector.plan(defWithTags)
      val plan4 = injector.plan(defWithTagsWithoutSugar)
      assert(plan3.definition == plan4.definition)

      val context = injector.produce(plan1).unsafeGet()
      val xInstance = context.get[TraitX]
      val yInstance = context.get[TraitY]("Y")
      val implInstance = context.get[ImplXYZ]("my-impl")

      assert(xInstance eq implInstance)
      assert(yInstance eq implInstance)
    }

    "support .named & .tagged calls after .from" in {
      import BasicCase6._

      val definition = new ModuleDef {
        make[TraitX]
          .from[ImplXYZ]
          .named("other")
          .tagged("x")
          .aliased[TraitX]("other2")

        make[ImplXYZ]
          .from[ImplXYZ]
          .tagged("xa")
          .aliased[TraitY]

        // can't call named after .named.from
        assertTypeError("""
        make[TraitX]
          .named("x1")
          .from[ImplXYZ]
          .named("xy")""")

        // can't call named twice
        assertTypeError("""
        make[TraitX]
          .named("x1")
          .named("x2")""")

        // can't call from after aliased
        assertTypeError("""
        make[TraitY]
          .aliased[TraitY]
          .from[ImplXYZ]""")

        // can't call tagged after aliased
        assertTypeError("""
        make[ImplXYZ]
          .aliased[TraitY]
          .tagged("xa")""")
      }

      assert(
        definition.bindings == Set(
          Bindings
            .binding[TraitX, ImplXYZ]
            .copy(
              key = DIKey.get[TraitX].named("other"),
              tags = Set("x"),
            ),
          Bindings.binding[ImplXYZ, ImplXYZ].addTags(Set("xa")),
          Bindings
            .reference[TraitX, TraitX]
            .copy(
              key = DIKey.get[TraitX].named("other2"),
              implementation = ImplDef.ReferenceImpl(SafeType.get[ImplXYZ], DIKey.get[TraitX].named("other"), weak = false),
              tags = Set("x"),
            ),
          Bindings.reference[TraitY, ImplXYZ].addTags(Set("xa")),
        )
      )
    }

    "support addImplicit with modifiers" in {
      implicit val dummy: DummyImplicit = DummyImplicit.dummyImplicit
      val definition = new ModuleDef {
        addImplicit[DummyImplicit]
        addImplicit[DummyImplicit].named("dummy")
      }

      assert(
        definition.bindings == Set(
          Bindings.binding[DummyImplicit, DummyImplicit](dummy),
          Bindings.binding[DummyImplicit, DummyImplicit](dummy).copy(key = DIKey.get[DummyImplicit].named("dummy")),
        )
      )
    }

    "print a sensible error message at compile-time when user tries to derive a constructor for a type parameter" in {
      val res1 = intercept[TestFailedException](
        assertCompiles(
          """
          def definition[T <: Int: Tag] = new ModuleDef {
            make[Int].from[T]
          }
        """
        )
      )
      assert(res1.getMessage contains "[T: AnyConstructor]")
      val res2 = intercept[TestFailedException](
        assertCompiles(
          """
          def definition[F[_]] = new ModuleDef {
            make[Int].fromResource[Lifecycle[F, Int]]
          }
        """
        )
      )
      assert(res2.getMessage contains "Wiring unsupported: `F[Unit]`")
      assert(res2.getMessage contains "trying to create an implementation")
      assert(res2.getMessage contains "`method release`")
      assert(res2.getMessage contains "`trait Lifecycle`")
    }

    "define multiple bindings with different axis but the same implementation" in {
      class X

      val module = new ModuleDef {
        make[X].tagged(Repo.Prod, Mode.Prod)
        make[X].tagged(Repo.Dummy, Mode.Prod)
        make[X].tagged(Mode.Test)
      }
      val bindings = (module ++ module).overridenBy(module).bindings
      assert(bindings.size == 3)
      assert(bindings.map(_.tags) == Set(Set[BindingTag](Repo.Prod, Mode.Prod), Set[BindingTag](Repo.Dummy, Mode.Prod), Set[BindingTag](Mode.Test)))
    }

  }

}
