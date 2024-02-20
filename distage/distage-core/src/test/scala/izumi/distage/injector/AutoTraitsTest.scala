package izumi.distage.injector

import izumi.distage.constructors.TraitConstructor
import izumi.distage.fixtures.TraitCases.*
import izumi.distage.fixtures.TypesCases.TypesCase3
import izumi.distage.fixtures.TypesCases.TypesCase6
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.reflection.TypedRef
import org.scalatest.wordspec.AnyWordSpec

import scala.language.reflectiveCalls

class AutoTraitsTest extends AnyWordSpec with MkInjector {

  trait Aaa {
    def a: Int
    def b: Boolean
  }

  "construct a basic trait" in {
    val traitCtor = TraitConstructor[Aaa].get

    val value = traitCtor.unsafeApply(Seq(TypedRef.byName(5), TypedRef.byName(false))).asInstanceOf[Aaa]

    assert(value.a == 5)
    assert(value.b == false)
  }

  "handle one-arg trait" in {
    import TraitCase1.*

    val definition = new ModuleDef {
      make[Dependency1]
      makeTrait[TestTrait]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(PlannerInput.everything(definition))

    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[TestTrait]
    assert(instantiated.isInstanceOf[TestTrait])
    assert(instantiated.dep != null)
  }

  "handle named one-arg trait" in {
    import TraitCase1.*

    val definition = new ModuleDef {
      make[Dependency1]
      make[TestTrait].named("named-trait").fromTrait[TestTrait]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(PlannerInput.everything(definition))

    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[TestTrait]("named-trait")
    assert(instantiated.isInstanceOf[TestTrait])
    assert(instantiated.dep != null)
  }

  "handle mixed sub-trait with protected autowires" in {
    import TraitCase2.*

    val definition = new ModuleDef {
      makeTrait[Trait3]
      makeTrait[Trait2]
      makeTrait[Trait1]
      make[Dependency3]
      make[Dependency2]
      make[Dependency1]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(PlannerInput.everything(definition))

    val context = injector.produce(plan).unsafeGet()
    val instantiated1 = context.get[Trait1]
    assert(instantiated1.isInstanceOf[Trait1])

    val instantiated2 = context.get[Trait2]
    assert(instantiated2.isInstanceOf[Trait2])

    val instantiated3 = context.get[Trait3]
    assert(instantiated3.isInstanceOf[Trait3])

    instantiated3.prr()
  }

  "handle sub-type trait" in {
    import TraitCase2.*

    val definition = new ModuleDef {
      make[Trait2].fromTrait[Trait3]
      make[Dependency3]
      make[Dependency2]
      make[Dependency1]
    }

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(PlannerInput.everything(definition))

    val context = injector.produce(plan).unsafeGet()
    val instantiated3 = context.get[Trait2]
    assert(instantiated3.isInstanceOf[Trait2])
    assert(instantiated3.asInstanceOf[Trait3].prr() == "Hello World")
  }

  "support trait fields" in {
    import TraitCase3.*

    val definition = PlannerInput.everything(new ModuleDef {
      makeTrait[ATraitWithAField]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)

    val context = injector.produce(plan).unsafeGet()
    assert(context.get[ATraitWithAField].field == 1)
  }

  "support named bindings in cglib traits" in {
    import TraitCase4.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dep].named("A").from[DepA]
      make[Dep].named("B").from[DepB]
      makeTrait[Trait]
      makeTrait[Trait1]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)

    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[Trait]
    val instantiated1 = context.get[Trait1]

    assert(instantiated.depA.isA)
    assert(!instantiated.depB.isA)

    assert(instantiated1.depA.isA)
    assert(!instantiated1.depB.isA)
  }

  "override protected defs in cglib traits" in {
    import TraitCase5.*

    val definition = PlannerInput.everything(new ModuleDef {
      makeTrait[TestTrait]
      make[Dep]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)

    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[TestTrait]

    assert(instantiated.rd == Dep().toString)
  }

  "can instantiate traits with refinements" in {
    import TraitCase5.*

    val definition = PlannerInput.everything(new ModuleDef {
      makeTrait[TestTraitAny { def dep: Dep }]
      make[Dep]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[TestTraitAny { def dep: Dep }]

    assert(instantiated.dep eq context.get[Dep])
  }

  "can instantiate structural types" in {
    val definition = PlannerInput.everything(new ModuleDef {
      makeTrait[{ def a: Int }]
      make[Int].from(5)
    })

    val injector = mkInjector()
    val context = injector.produce(definition).unsafeGet()

    val instantiated = context.get[{ def a: Int }]
    assert(instantiated.a == context.get[Int])
  }

  "can instantiate intersection types" in {
    import TypesCase3.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dep]
      make[Dep2]
      makeTrait[Trait2 & (Trait2 & (Trait2 & Trait1))]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[Trait2 & Trait1]

    assert(instantiated.dep eq context.get[Dep])
    assert(instantiated.dep2 eq context.get[Dep2])
  }

  "can instantiate intersection types with implicit overrides" in {
    import TypesCase6.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dep]
      make[Dep2]
      makeTrait[Trait1 & Trait2]
    })

    val injector = mkNoCyclesInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[Trait2 & Trait1]

    assert(instantiated.dep ne context.get[Dep])
    assert(instantiated.dep eq context.get[Dep2])
  }

  "can handle AnyVals" in {
    import TraitCase6.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dep]
      make[AnyValDep]
      makeTrait[TestTrait]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TestTrait].anyValDep ne null)
    // AnyVal reboxing happened
    assert(context.get[TestTrait].anyValDep ne context.get[AnyValDep].asInstanceOf[AnyRef])
    assert(context.get[TestTrait].anyValDep.d eq context.get[Dep])
  }

  "can handle abstract classes" in {
    import TraitCase7.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dependency1]
      make[Dependency2]
      make[X].fromTrait[XImpl]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)
    val context = injector.produce(plan).unsafeGet()

    val dependency1 = context.get[Dependency1]
    val dependency2 = context.get[Dependency2]

    assert(context.get[X].get() == Result(dependency1, dependency2))
    assert(context.get[X].get().dependency1 eq dependency1)
    assert(context.get[X].get().dependency2 eq dependency2)
  }

}
