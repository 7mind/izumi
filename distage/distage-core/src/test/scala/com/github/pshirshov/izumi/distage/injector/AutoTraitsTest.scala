package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.TraitCases._
import distage.{ModuleBase, ModuleDef}
import org.scalatest.WordSpec

class AutoTraitsTest extends WordSpec with MkInjector {

  "support trait fields" in {
    val definition: ModuleBase = new ModuleDef {
      make[TraitCase3.ATraitWithAField]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    assert(context.get[TraitCase3.ATraitWithAField].field == 1)
  }

  "support named bindings in cglib traits" in {
    import TraitCase4._

    val definition = new ModuleDef {
      make[Dep].named("A").from[DepA]
      make[Dep].named("B").from[DepB]
      make[Trait]
      make[Trait1]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val instantiated = context.get[Trait]

    assert(instantiated.depA.isA)
    assert(!instantiated.depB.isA)

    val instantiated1 = context.get[Trait1]

    assert(instantiated1.depA.isA)
    assert(!instantiated1.depB.isA)
  }

  "override protected defs in cglib traits" in {
    import TraitCase5._

    val definition = new ModuleDef {
      make[TestTrait]
      make[Dep]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val instantiated = context.get[TestTrait]

    assert(instantiated.rd == Dep().toString)
  }

}
