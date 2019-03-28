package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.BasicCases._
import com.github.pshirshov.izumi.distage.fixtures.SetCases._
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.definition.Binding.{SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, BindingTag, Id, ImplDef}
import com.github.pshirshov.izumi.distage.model.exceptions.{BadIdAnnotationException, ProvisioningException, UnsupportedWiringException, ConflictingDIKeyBindingsException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ImportDependency
import com.github.pshirshov.izumi.distage.reflection.SymbolIntrospectorDefaultImpl
import distage._
import org.scalatest.WordSpec

class BasicTest extends WordSpec with MkInjector {

  "maintain correct operation order" in {
    import BasicCase1._
    val definition = PlannerInput(new ModuleDef {
      make[TestClass]
      make[TestDependency3]
      make[TestDependency0].from[TestImpl0]
      make[TestDependency1]
      make[TestCaseClass]
      make[LocatorDependent]
      make[TestInstanceBinding].from(TestInstanceBinding())
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    assert(plan.steps.exists(_.isInstanceOf[ImportDependency]))

    val exc = intercept[ProvisioningException] {
      injector.produceUnsafe(plan)
    }

    assert(exc.getMessage.startsWith("Provisioner stopped after 1 instances, 1/9 operations failed"))

    val fixedPlan = plan.resolveImports {
      case i if i.target == DIKey.get[NotInContext] => new NotInContext {}
    }
    val locator = injector.produceUnsafe(fixedPlan)
    assert(locator.get[LocatorDependent].ref.get == locator)
  }

  "correctly handle empty typed sets" in {
    import SetCase1._

    val definition = PlannerInput(new ModuleDef {
      make[TypedService[Int]].from[ServiceWithTypedSet]
      many[ExampleTypedCaseClass[Int]]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val s = context.get[TypedService[Int]]
    val ss = context.get[Set[ExampleTypedCaseClass[Int]]]
    assert(s.isInstanceOf[TypedService[Int]])
    assert(ss.isEmpty)
  }


  "fails on wrong @Id annotation" in {
    import BadAnnotationsCase._
    val definition = PlannerInput(new ModuleDef {
      make[TestDependency0]
      make[TestClass]
    })

    val injector = mkInjector()

    val exc = intercept[BadIdAnnotationException] {
      injector.plan(definition)
    }

    assert(exc.getMessage == "Wrong annotation value, only constants are supported. Got: @com.github.pshirshov.izumi.distage.model.definition.Id(com.github.pshirshov.izumi.distage.model.definition.Id(BadAnnotationsCase.this.value))")
  }

  "support multiple bindings" in {
    import BasicCase1._
    val definition = PlannerInput(new ModuleDef {
      many[JustTrait].named("named.empty.set")

      many[JustTrait]
        .add[JustTrait]
        .add(new Impl1)

      many[JustTrait].named("named.set")
        .add(new Impl2())

      many[JustTrait].named("named.set")
        .add[Impl3]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[Set[JustTrait]].size == 2)
    assert(context.get[Set[JustTrait]]("named.empty.set").isEmpty)
    assert(context.get[Set[JustTrait]]("named.set").size == 2)
  }


  "support nested multiple bindings" in {
    // https://github.com/pshirshov/izumi-r2/issues/261
    import BasicCase1._
    val definition = PlannerInput(new ModuleDef {
      many[JustTrait]
        .add(new Impl1)
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val sub = Injector.inherit(context)
    val subplan = sub.plan(definition)
    val subcontext = injector.produceUnsafe(subplan)

    assert(context.get[Set[JustTrait]].size == 1)
    assert(subcontext.get[Set[JustTrait]].size == 1)
  }

  "support named bindings" in {
    import BasicCase2._
    val definition = PlannerInput(new ModuleDef {
      make[TestClass]
        .named("named.test.class")
      make[TestDependency0].from[TestImpl0Bad]
      make[TestDependency0].named("named.test.dependency.0")
        .from[TestImpl0Good]
      make[TestInstanceBinding].named("named.test")
        .from(TestInstanceBinding())
      make[TestDependency0].namedByImpl
        .from[TestImpl0Good]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[TestClass]("named.test.class").correctWired())
  }

  "fail on unbindable" in {
    import BasicCase3._

    val definition = PlannerInput(new ModuleBase {
      override def bindings: Set[Binding] = Set(
        SingletonBinding(DIKey.get[Dependency], ImplDef.TypeImpl(SafeType.get[Long]))
      )
    })

    val injector = mkInjector()
    intercept[UnsupportedWiringException] {
      injector.plan(definition)
    }
  }

  "fail on unsolvable conflicts" in {
    import BasicCase3._

    val definition = PlannerInput(new ModuleDef {
      make[Dependency].from[Impl1]
      make[Dependency].from[Impl2]
    })

    val injector = mkInjector()
    val exc = intercept[ConflictingDIKeyBindingsException] {
      injector.plan(definition)
    }
    assert(exc.conflicts.size == 1 && exc.conflicts.contains(DIKey.get[Dependency]))
  }

  // BasicProvisionerTest
  "instantiate simple class" in {
    import BasicCase1._
    val definition = PlannerInput(new ModuleDef {
      make[TestCaseClass2]
      make[TestInstanceBinding].from(new TestInstanceBinding)
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)
    val instantiated = context.get[TestCaseClass2]

    assert(instantiated.a.z.nonEmpty)
  }

  "handle set bindings" in {
    import SetCase1._

    val definition = PlannerInput(new ModuleDef {
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
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produceUnsafe(plan)

    assert(context.get[Service0].set.size == 3)
    assert(context.get[Service1].set.size == 3)
    assert(context.get[Service2].set.size == 3)
    assert(context.get[Service3].set.size == 3)
  }

  "support Plan.providerImport and Plan.resolveImport" in {
    import BasicCase1._

    val definition = PlannerInput(new ModuleDef {
      make[TestCaseClass2]
    })

    val injector = mkInjector()

    val plan1 = injector.plan(definition)
    val plan2 = injector.finish(plan1.providerImport {
      verse: String@Id("verse") =>
        TestInstanceBinding(verse)
    })
    val plan3 = plan2.resolveImport[String](id = "verse") {
      """ God only knows what I might do, god only knows what I might do, I don't fuck with god, I'm my own through
        | Take two of these feel like Goku""".stripMargin
    }

    val context = injector.produceUnsafe(plan3)

    assert(context.get[TestCaseClass2].a.z == context.get[String]("verse"))
  }

  "preserve type annotations" in {
    import BasicCase4._

    val definition = PlannerInput(new ModuleDef {
      make[Dependency].named("special")
      make[TestClass]
    })

    val injector = mkInjector()

    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[TestClass] != null)
  }

  "handle reflected constructor references" in {
    import BasicCase4._

    val symbolIntrospector = new SymbolIntrospectorDefaultImpl.Runtime
    val safeType = SafeType.get[ClassTypeAnnT[String, Int]]
    val constructor = symbolIntrospector.selectConstructor(safeType)

    val allArgsHaveAnnotations = constructor.map(_.arguments.flatten.map(_.annotations)).toSeq.flatten.forall(_.nonEmpty)
    assert(allArgsHaveAnnotations)
  }

  "handle set inclusions" in {
    val definition = PlannerInput(new ModuleDef {
      make[Set[Int]].named("x").from(Set(1, 2, 3))
      make[Set[Int]].named("y").from(Set(4, 5, 6))
      many[Int].refSet[Set[Int]]("x")
      many[Int].refSet[Set[Int]]("y")

      make[Set[None.type]].from(Set(None))
      make[Set[Some[Int]]].from(Set(Some(7)))
      many[Option[Int]].refSet[Set[None.type]]
      many[Option[Int]].refSet[Set[Some[Int]]]
    })

    val context = Injector.Standard().produceUnsafe(definition)

    assert(context.get[Set[Int]] == Set(1, 2, 3, 4, 5, 6))
    assert(context.get[Set[Option[Int]]] == Set(None, Some(7)))
  }

  "handle multiple set element binds" in {
    val definition = PlannerInput(new ModuleDef {
      make[Int].from(7)

      many[Int].add(5)
      many[Int].add(0)

      many[Int].addSet(Set(1, 2, 3))

      many[Int].add { i: Int => i - 1 }
      many[Int].addSet {
        i: Int =>
          Set(i, i + 1, i + 2)
      }
    })

    val context = Injector.Standard().produceUnsafe(definition)

    assert(context.get[Set[Int]] == Set(0, 1, 2, 3, 5, 6, 7, 8, 9))
  }

  "support empty sets" in {
    import BasicCase5._
    val definition = PlannerInput(new ModuleDef {
      many[TestDependency]
      make[TestImpl1]
    })

    val context = Injector.Standard().produceUnsafe(definition)

    assert(context.get[TestImpl1].justASet == Set.empty)
  }

  "preserve tags in multi set bindings" in {

    val definition = PlannerInput(new ModuleDef {
      many[Int].named("zzz")
        .add(5).tagged("t3")
        .addSet(Set(1, 2, 3)).tagged("t1", "t2")
        .addSet(Set(1, 2, 3)).tagged("t3", "t4")
    })

    assert(definition.bindings.bindings.collectFirst {
      case SetElementBinding(_, _, s, _) if Set(BindingTag("t1"), BindingTag("t2")).diff(s).isEmpty => true
    }.nonEmpty)
  }
}
