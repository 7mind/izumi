package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.definition.CompileTimeDSL._
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.exceptions.{MissingInstanceException, TraitInitializationFailedException, UnsupportedWiringException, UntranslatablePlanException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.PlanningFailure.{DuplicatedStatements, UnsolvableConflict}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse.Wiring.UnaryWiring
import org.scalatest.WordSpec

class InjectorTest extends WordSpec {

  def mkInjector(): Injector = Injector.emerge()

  "DI planner" should {

    "maintain correct operation order" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .binding[TestClass]
        .binding[TestDependency3]
        .binding[TestDependency0, TestImpl0]
        .binding[TestDependency1]
        .binding[TestCaseClass]
        .instance(TestInstanceBinding())

      val injector = mkInjector()
      val plan = injector.plan(definition)
      assert(plan.steps.exists(_.isInstanceOf[ImportDependency]))

      intercept[MissingInstanceException] {
        injector.produce(plan)
      }

      val fixedPlan = plan.flatMap {
        case ImportDependency(key, _) if key == RuntimeUniverse.DIKey.get[NotInContext] =>
          Seq(WiringOp.ReferenceInstance(
            key
            , UnaryWiring.Instance(RuntimeUniverse.SafeType.get[NotInContext], new NotInContext {})
          ))

        case op =>
          Seq(op)
      }
      injector.produce(fixedPlan)
    }

    "support multiple bindings" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .set[JustTrait]
          .named("named.empty.set")
        .set[JustTrait]
        .element[JustTrait, Impl0]
        .element[JustTrait](new Impl1)
        .element[JustTrait](new Impl2())
          .named("named.set")
        .element[JustTrait, Impl3]
          .named("named.set")

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[Set[JustTrait]]("named.set").size == 2)
    }

    "support named bindings" in {
      import Case1_1._
      val definition: ContextDefinition = TrivialDIDef
        .binding[TestClass]
          .named("named.test.class")
        .binding[TestDependency0, TestImpl0Bad]
        .binding[TestDependency0, TestImpl0Good]
          .named("named.test.dependency.0")
        .instance(TestInstanceBinding())
          .named("named.test")

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      println(context.get[TestClass]("named.test.class"))
      assert(context.get[TestClass]("named.test.class").correctWired())
    }

    "support circular dependencies" in {
      import Case2._

      val definition: ContextDefinition = TrivialDIDef
        .binding[Circular2]
        .binding[Circular1]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
    }

    "support complex circular dependencies" in {
      import Case3._

      val definition: ContextDefinition = TrivialDIDef
        .binding[Circular3]
        .binding[Circular1]
        .binding[Circular2]
        .binding[Circular5]
        .binding[Circular4]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      val c3 = context.get[Circular3]
      val traitArg = c3.arg
      assert(traitArg != null && traitArg.isInstanceOf[Circular4])
      assert(c3.method == 2L)
      assert(traitArg.testVal == 1)
      context.enumerate.foreach(println)
    }

    "support generics" in {
      import Case11._

      val definition = TrivialDIDef
        .instance[List[Dep]](List(DepA())).named("As")
        .instance[List[Dep]](List(DepB())).named("Bs")
        .instance[List[DepA]](List(DepA(), DepA(), DepA()))
        .binding[TestClass[DepA]]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[List[Dep]]("As").forall(_.isInstanceOf[DepA]))
      assert(context.get[List[DepA]].forall(_.isInstanceOf[DepA]))
      assert(context.get[List[Dep]]("Bs").forall(_.isInstanceOf[DepB]))
      assert(context.get[TestClass[DepA]].inner == context.get[List[DepA]])
    }

//  TODO:
//    "support classes with typealiases" in {
//      import Case11._
//
//      val definition = TrivialDIDef
//        .binding[DepA]
//        .binding[TestClass2[TypeAliasDepA]]
//
//      val injector = mkInjector()
//      val plan = injector.plan(definition)
//      val context = injector.produce(plan)
//
//      assert(context.get[TestTrait].dep.isInstanceOf[TypeAliasDepA])
//    }

    "support traits with typealiases" in {
      import Case11._

      val definition = TrivialDIDef
        .binding[DepA]
        .binding[TestTrait]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestTrait].dep.isInstanceOf[TypeAliasDepA])
    }

    "support trait initialization" in {
      import Case3._

      val definition: ContextDefinition = TrivialDIDef
        .binding[CircularBad1]
        .binding[CircularBad2]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val exc = intercept[TraitInitializationFailedException] {
        injector.produce(plan)
      }
      assert(exc.getCause.isInstanceOf[RuntimeException])

    }

    "support trait fields" in {
      val definition: ContextDefinition = TrivialDIDef
        .binding[Case9.ATraitWithAField]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      assert(context.get[Case9.ATraitWithAField].field == 1)
    }

    "fail on unbindable" in {
      import Case4._

      val definition: ContextDefinition = new ContextDefinition {

        import BindingT._

        override def bindings: Seq[Binding] = Seq(
          SingletonBinding(RuntimeUniverse.DIKey.get[Dependency], ImplDef.TypeImpl(RuntimeUniverse.SafeType.get[Long]))
        )
      }

      val injector = mkInjector()
      intercept[UnsupportedWiringException] {
        injector.plan(definition)
      }
      //assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[UnbindableBinding]))
    }

    "fail on unsolvable conflicts" in {
      import Case4._

      val definition: ContextDefinition = TrivialDIDef
        .binding[Dependency, Impl1]
        .binding[Dependency, Impl2]

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[UnsolvableConflict]))
    }

    "handle exactly the same ops" in {
      import Case4._

      val definition: ContextDefinition = TrivialDIDef
        .binding[Dependency, Impl1]
        .binding[Dependency, Impl1]

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[DuplicatedStatements]))

    }

    "handle factory injections" in {
      import Case5._

      val definition: ContextDefinition = TrivialDIDef
        .binding[Factory]
        .binding[Dependency]
        .binding[OverridingFactory]
        .binding[AssistedFactory]
        .binding[AbstractFactory]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val factory = context.get[Factory]
      assert(factory.wiringTargetForDependency != null)
      assert(factory.factoryMethodForDependency() != factory.wiringTargetForDependency)
      assert(factory.x().b.isInstanceOf[Dependency])

      val abstractFactory = context.get[AbstractFactory]
      assert(abstractFactory.x().isInstanceOf[AbstractDependencyImpl])

      val overridingFactory = context.get[OverridingFactory]
      assert(overridingFactory.x(ConcreteDep()).b.isInstanceOf[ConcreteDep])

      val assistedFactory = context.get[AssistedFactory]
      assert(assistedFactory.x(1).a == 1)
      assert(assistedFactory.x(1).b.isInstanceOf[Dependency])
    }

    "handle generic arguments in cglib factory methods" in {
      import Case5._

      val definition: ContextDefinition = TrivialDIDef
        .binding[GenericAssistedFactory]
        .instance[Dependency](ConcreteDep())

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[GenericAssistedFactory]
      val product = instantiated.x(List(SpecialDep()), List(5))
      assert(product.a.forall(_.isSpecial))
      assert(product.b.forall(_ == 5))
      assert(product.c == ConcreteDep())
    }

    "handle named assisted dependencies in cglib factory methods" in {
      import Case5._

      val definition: ContextDefinition = TrivialDIDef
        .binding[NamedAssistedFactory]
        .binding[Dependency]
        .instance[Dependency](SpecialDep()).named("special")

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[NamedAssistedFactory]

      assert(instantiated.x(5).b.isSpecial)
    }

    // TODO GenericFactory polymorphic arguments in producer methods
    "handle macro factory injections" in {
      import Case5._

      val definition: ContextDefinition = TrivialDIDef
        .magic[Factory]
        .magic[Dependency]
        .magic[OverridingFactory]
        .magic[AssistedFactory]
        .magic[AbstractFactory]

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val factory = context.get[Factory]
      assert(factory.wiringTargetForDependency != null)
      assert(factory.factoryMethodForDependency() != factory.wiringTargetForDependency)
      assert(factory.x().b.isInstanceOf[Dependency])

      val abstractFactory = context.get[AbstractFactory]
      assert(abstractFactory.x().isInstanceOf[AbstractDependencyImpl])

      val overridingFactory = context.get[OverridingFactory]
      assert(overridingFactory.x(ConcreteDep()).b.isInstanceOf[ConcreteDep])

      val assistedFactory = context.get[AssistedFactory]
      assert(assistedFactory.x(1).a == 1)
      assert(assistedFactory.x(1).b.isInstanceOf[Dependency])
    }

    "handle generic arguments in macro factory methods" in {
      import Case5._

      val definition: ContextDefinition = TrivialDIDef
        .magic[GenericAssistedFactory]
        .instance[Dependency](ConcreteDep())

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[GenericAssistedFactory]
      val product = instantiated.x(List(SpecialDep()), List(5))
      assert(product.a.forall(_.isSpecial))
      assert(product.b.forall(_ == 5))
      assert(product.c == ConcreteDep())
    }

    "handle assisted dependencies in macro factory methods" in {
      import Case5._

      val definition: ContextDefinition = TrivialDIDef
        .magic[AssistedFactory]
        .instance[Dependency](ConcreteDep())

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[AssistedFactory]

      assert(instantiated.x(5).a == 5)
    }

//  TODO:
//    "handle named assisted dependencies in macro factory methods" in {
//      import Case5._
//
//      val definition: ContextDefinition = TrivialDIDef
//        .magic[NamedAssistedFactory]
//        .magic[Dependency]
//        .instance[Dependency](SpecialDep()).named("special")
//
//      val injector = mkInjector()
//      val plan = injector.plan(definition)
//      val context = injector.produce(plan)
//
//      val instantiated = context.get[NamedAssistedFactory]
//
//      assert(instantiated.x(5).b.isSpecial)
//    }

    // BasicProvisionerTest
    "instantiate simple class" in {
      import Case1._
      val definition: ContextDefinition = TrivialDIDef
        .binding[TestCaseClass2]
        .instance(new TestInstanceBinding)

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      val instantiated = context.get[TestCaseClass2]

      println(s"Got instance: ${instantiated.toString}!")
    }

    "instantiate provider bindings" in {
      import Case6._

      val definition: ContextDefinition = TrivialDIDef
        .provider[TestClass]((a: Dependency1) => new TestClass(null) )
        .provider[Dependency1](() => new Dependency1Sub {})

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      println(context.parent.get.plan)
      val instantiated = context.get[TestClass]

      println(s"Got instance: ${instantiated.toString}!")
    }

    "handle one-arg trait" in {
      import Case7._

      val definition = TrivialDIDef
        .binding[Dependency1]
        .magic[TestTrait]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]
      assert(instantiated.isInstanceOf[TestTrait])

      println(s"Got instance: ${instantiated.toString}!")
    }

    "handle named one-arg trait" in {
      import Case7._

      val definition = TrivialDIDef
        .binding[Dependency1]
        .magic[TestTrait]
          .named("named-trait")

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]("named-trait")
      assert(instantiated.isInstanceOf[TestTrait])

      println(s"Got instance: ${instantiated.toString}!")
    }

    "handle mixed sub-trait with protected autowires" in {
      import Case8._

      val definition = TrivialDIDef
        .magic[Trait3]
        .magic[Trait2]
        .magic[Trait1]
        .binding[Dependency3]
        .binding[Dependency2]
        .binding[Dependency1]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated1 = context.get[Trait1]
      assert(instantiated1.isInstanceOf[Trait1])

      val instantiated2 = context.get[Trait2]
      assert(instantiated2.isInstanceOf[Trait2])

      val instantiated3 = context.get[Trait3]
      assert(instantiated3.isInstanceOf[Trait3])

      instantiated3.prr()
    }

    "handle sub-type trait" in {
      import Case8._

      val definition = TrivialDIDef
        .magic[Trait2, Trait3]
        .binding[Dependency3]
        .binding[Dependency2]
        .binding[Dependency1]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated3 = context.get[Trait2]
      assert(instantiated3.isInstanceOf[Trait2])
      assert(instantiated3.asInstanceOf[Trait3].prr() == "Hello World")
    }

    "handle generics" in {
      import Case12._

      val definition = TrivialDIDef
        .binding[Parameterized[Dep]]
        .binding[ParameterizedTrait[Dep]]
        .binding[Dep]

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      assert(context.get[Parameterized[Dep]].t.isInstanceOf[Dep])
      assert(context.get[ParameterizedTrait[Dep]].t.isInstanceOf[Dep])
    }

  }

//  TODO:
//  "support named bindings in macro traits" in {
//    import Case10._
//
//    val definition = TrivialDIDef
//      .magic[Dep, DepA].named("A")
//      .magic[Dep, DepB].named("B")
//      .magic[Trait]
//
//    val injector = mkInjector()
//    val plan = injector.plan(definition)
//
//    val context = injector.produce(plan)
//    val instantiated = context.get[Trait]
//
//    assert(instantiated.depA.isA)
//    assert(!instantiated.depB.isA)
//  }

  "support named bindings in cglib traits" in {
    import Case10._

    val definition = TrivialDIDef
      .binding[Dep, DepA].named("A")
      .binding[Dep, DepB].named("B")
      .binding[Trait]

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val instantiated = context.get[Trait]

    assert(instantiated.depA.isA)
    assert(!instantiated.depB.isA)
  }

  "populate implicit parameters in class constructor from explicit DI context instead of scala's implicit resolution" in {
    import Case13._

    val definition = TrivialDIDef
      .binding[TestClass]
      .binding[Dep]
      .binding[DummyImplicit, MyDummyImplicit]

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val instantiated = context.get[TestClass]

    assert(instantiated.dummyImplicit.isInstanceOf[MyDummyImplicit])
    assert(instantiated.dummyImplicit.asInstanceOf[MyDummyImplicit].imADummy)
  }

  "override protected defs in cglib traits" in {
    import Case14._

    val definition = TrivialDIDef
      .binding[TestTrait]
      .binding[Dep]

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val instantiated = context.get[TestTrait]

    assert(instantiated.rd == Dep().toString)
  }

  "override protected defs in macro traits" in {
    import Case14._

    val definition = TrivialDIDef
      .magic[TestTrait]
      .magic[Dep]

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val instantiated = context.get[TestTrait]

    assert(instantiated.rd == Dep().toString)
  }

}
