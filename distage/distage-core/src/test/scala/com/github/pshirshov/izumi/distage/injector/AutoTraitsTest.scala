package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.TraitCases._
import com.github.pshirshov.izumi.distage.fixtures.TypesCases.TypesCase3
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.exceptions.ProvisioningException
import com.github.pshirshov.izumi.fundamentals.reflection.MethodMirrorException
import distage.ModuleDef
import org.scalatest.WordSpec

import scala.language.reflectiveCalls

class AutoTraitsTest extends WordSpec with MkInjector {

  "support trait fields" in {
    val definition = PlannerInput(new ModuleDef {
      make[TraitCase3.ATraitWithAField]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    assert(context.get[TraitCase3.ATraitWithAField].field == 1)
  }

  "support named bindings in cglib traits" in {
    import TraitCase4._

    val definition = PlannerInput(new ModuleDef {
      make[Dep].named("A").from[DepA]
      make[Dep].named("B").from[DepB]
      make[Trait]
      make[Trait1]
    })

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

    val definition = PlannerInput(new ModuleDef {
      make[TestTrait]
      make[Dep]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan)
    val instantiated = context.get[TestTrait]

    assert(instantiated.rd == Dep().toString)
  }

  "progression test: can't instantiate traits with refinements" in {
    val ex = intercept[ProvisioningException] {
      import TraitCase5._

      val definition = PlannerInput(new ModuleDef {
        make[TestTrait {def dep: Dep}]
        make[Dep]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]

      assert(instantiated.rd == Dep().toString)
    }
    assert(ex.getSuppressed.exists(_.isInstanceOf[MethodMirrorException]))
  }

  "progression test: can't instantiate `with` types" in {
    val ex = intercept[ProvisioningException] {
      import TypesCase3._

      val definition = PlannerInput(new ModuleDef {
        make[Dep]
        make[Dep2]
        make[Trait2 with Trait1]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[Trait2 with Trait1]

      assert(instantiated.dep == context.get[Dep])
      assert(instantiated.dep2 == context.get[Dep])
    }
    assert(ex.getSuppressed.exists(_.isInstanceOf[MethodMirrorException]))
  }

  "handle refinement & structural types" in {
    import TypesCase3._

    val definition = PlannerInput(new ModuleDef {
      make[Dep]
      make[Dep2]
      make[Trait1 {def dep: Dep2}].from[Trait3[Dep2]]
      make[ {def dep: Dep}].from[Trait6]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instantiated1: Trait1 {def dep: Dep2} = context.get[Trait1 {def dep: Dep2}]
    val instantiated2 = context.get[ {def dep: Dep}]

    assert(instantiated1.dep == context.get[Dep2])
    assert(instantiated2.dep == context.get[Dep])
  }

}
