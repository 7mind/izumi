package izumi.distage.injector

import distage.ModuleDef
import izumi.distage.fixtures.TraitCases._
import izumi.distage.fixtures.TypesCases.TypesCase3
import izumi.distage.model.PlannerInput
import org.scalatest.wordspec.AnyWordSpec

class AutoTraitsTest extends AnyWordSpec with MkInjector {

  "support trait fields" in {
    import TraitCase3._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[ATraitWithAField]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan).unsafeGet()
    assert(context.get[ATraitWithAField].field == 1)
  }

  "support named bindings in cglib traits" in {
    import TraitCase4._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Dep].named("A").from[DepA]
      make[Dep].named("B").from[DepB]
      make[Trait]
      make[Trait1]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[Trait]
    val instantiated1 = context.get[Trait1]

    assert(instantiated.depA.isA)
    assert(!instantiated.depB.isA)

    assert(instantiated1.depA.isA)
    assert(!instantiated1.depB.isA)
  }

  "override protected defs in cglib traits" in {
    import TraitCase5._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestTrait]
      make[Dep]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[TestTrait]

    assert(instantiated.rd == Dep().toString)
  }

  "can instantiate traits with refinements" in {
    import TraitCase5._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[TestTraitAny {def dep: Dep}]
      make[Dep]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[TestTraitAny {def dep: Dep}]

    assert(instantiated.dep eq context.get[Dep])
  }

  "can instantiate `with` types" in {
    import TypesCase3._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Dep]
      make[Dep2]
      make[Trait2 with Trait1]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val instantiated = context.get[Trait2 with Trait1]

    assert(instantiated.dep eq context.get[Dep])
    assert(instantiated.dep2 eq context.get[Dep2])
  }

  "can handle AnyVals" in {
    import TraitCase6._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Dep]
      make[AnyValDep].from(AnyValDep(_))
      make[TestTrait]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TestTrait].anyValDep != null)
    assert(context.get[TestTrait].anyValDep ne context.get[AnyValDep].asInstanceOf[AnyRef])
    assert(context.get[TestTrait].anyValDep.d eq context.get[Dep])
  }

  "can handle abstract classes" in {
    import TraitCase7._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Dependency1]
      make[Dependency2]
      make[X].from[XImpl]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val dependency1 = context.get[Dependency1]
    val dependency2 = context.get[Dependency2]

    assert(context.get[X].get() == Result(dependency1, dependency2))
    assert(context.get[X].get().dependency1 eq dependency1)
    assert(context.get[X].get().dependency2 eq dependency2)
  }

}
