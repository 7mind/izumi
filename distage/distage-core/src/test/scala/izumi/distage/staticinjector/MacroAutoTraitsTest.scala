package izumi.distage.staticinjector

import izumi.distage.constructors.AnyConstructor
import izumi.distage.fixtures.TraitCases.{TraitCase1, TraitCase2, TraitCase4, TraitCase5}
import izumi.distage.fixtures.TypesCases.TypesCase3
import izumi.distage.injector.MkInjector
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.TypedRef
import org.scalatest.wordspec.AnyWordSpec

class MacroAutoTraitsTest extends AnyWordSpec with MkInjector {

  "construct a basic trait" in {
    val traitCtor = AnyConstructor[Aaa].provider.get

    val value = traitCtor.unsafeApply(Seq(TypedRef.byName(5), TypedRef.byName(false))).asInstanceOf[Aaa]

    assert(value.a == 5)
    assert(value.b == false)
  }

  "handle one-arg trait" in {
    import TraitCase1._

    val definition = new ModuleDef {
      make[Dependency1]
      make[TestTrait]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(PlannerInput.noGc(definition))

    val context = injector.produceUnsafe(plan)
    val instantiated = context.get[TestTrait]
    assert(instantiated.isInstanceOf[TestTrait])
    assert(instantiated.dep != null)
  }

  "handle named one-arg trait" in {
    import TraitCase1._

    val definition = new ModuleDef {
      make[Dependency1]
      make[TestTrait].named("named-trait").from[TestTrait]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(PlannerInput.noGc(definition))

    val context = injector.produceUnsafe(plan)
    val instantiated = context.get[TestTrait]("named-trait")
    assert(instantiated.isInstanceOf[TestTrait])
    assert(instantiated.dep != null)
  }

  "handle mixed sub-trait with protected autowires" in {
    import TraitCase2._

    val definition = new ModuleDef {
      make[Trait3]
      make[Trait2]
      make[Trait1]
      make[Dependency3]
      make[Dependency2]
      make[Dependency1]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(PlannerInput.noGc(definition))

    val context = injector.produceUnsafe(plan)
    val instantiated1 = context.get[Trait1]
    assert(instantiated1.isInstanceOf[Trait1])

    val instantiated2 = context.get[Trait2]
    assert(instantiated2.isInstanceOf[Trait2])

    val instantiated3 = context.get[Trait3]
    assert(instantiated3.isInstanceOf[Trait3])

    instantiated3.prr()
  }

  "handle sub-type trait" in {
    import TraitCase2._

    val definition = new ModuleDef {
      make[Trait2].from[Trait3]
      make[Dependency3]
      make[Dependency2]
      make[Dependency1]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(PlannerInput.noGc(definition))

    val context = injector.produceUnsafe(plan)
    val instantiated3 = context.get[Trait2]
    assert(instantiated3.isInstanceOf[Trait2])
    assert(instantiated3.asInstanceOf[Trait3].prr() == "Hello World")
  }

  "can instantiate traits with refinements" in {
    import TraitCase5._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestTraitAny {def dep: Dep}]
      make[Dep]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)
    val instantiated = context.get[TestTraitAny {def dep: Dep}]

    assert(instantiated.dep eq context.get[Dep])
  }

  "can instantiate `with` types" in {
    import TypesCase3._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Dep]
      make[Dep2]
      make[Trait2 with (Trait2 with (Trait2 with Trait1))]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val instantiated = context.get[Trait2 with (Trait2 with (Trait2 with Trait1))]

    assert(instantiated.dep eq context.get[Dep])
    assert(instantiated.dep2 eq context.get[Dep2])
  }

  "support named bindings in macro traits" in {
    import TraitCase4._

    val definition = new ModuleDef {
      make[Dep].named("A").from[DepA]
      make[Dep].named("B").from[DepB]
      make[Trait]
      make[Trait1]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(PlannerInput.noGc(definition))

    val context = injector.produceUnsafe(plan)
    val instantiated = context.get[Trait]

    assert(instantiated.depA.isA)
    assert(!instantiated.depB.isA)

    val instantiated1 = context.get[Trait1]

    assert(instantiated1.depA.isA)
    assert(!instantiated1.depB.isA)
  }

  "override protected defs in macro traits" in {
    import TraitCase5._

    val definition = new ModuleDef {
      make[TestTrait]
      make[Dep]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(PlannerInput.noGc(definition))

    val context = injector.produceUnsafe(plan)
    val instantiated = context.get[TestTrait]

    assert(instantiated.rd == Dep().toString)
  }

  "handle AnyVals" in {
    import izumi.distage.fixtures.TraitCases.TraitCase6._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Dep]
      make[AnyValDep]
      make[TestTrait]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[TestTrait].anyValDep != null)

    // AnyVal reboxing happened
    assert(context.get[TestTrait].anyValDep ne context.get[AnyValDep].asInstanceOf[AnyRef])
    assert(context.get[TestTrait].anyValDep.d eq context.get[Dep])
  }

  trait Aaa {
    def a: Int
    def b: Boolean
  }

}
