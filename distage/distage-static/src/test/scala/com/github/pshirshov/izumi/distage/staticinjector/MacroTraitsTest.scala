package com.github.pshirshov.izumi.distage.staticinjector

import com.github.pshirshov.izumi.distage.fixtures.TraitCases.{TraitCase1, TraitCase2, TraitCase4, TraitCase5}
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.definition.StaticModuleDef
import org.scalatest.WordSpec
import com.github.pshirshov.izumi.distage.provisioning.AnyConstructor
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TypedRef

class MacroTraitsTest extends WordSpec with MkInjector {

  "construct a basic trait" in {
    val traitCtor = AnyConstructor[Aaa].provider.get

    val value = traitCtor.unsafeApply(TypedRef(5), TypedRef(false)).asInstanceOf[Aaa]

    assert(value.a == 5)
    assert(value.b == false)
  }

  "handle one-arg trait" in {
    import TraitCase1._

    val definition = new StaticModuleDef {
      stat[Dependency1]
      stat[TestTrait]
    }

    val injector = mkInjector()
    val plan = injector.plan(PlannerInput(definition))

    val context = injector.produce(plan)
    val instantiated = context.get[TestTrait]
    assert(instantiated.isInstanceOf[TestTrait])
    assert(instantiated.dep != null)
  }

  "handle named one-arg trait" in {
    import TraitCase1._

    val definition = new StaticModuleDef {
      stat[Dependency1]
      make[TestTrait].named("named-trait").stat[TestTrait]
    }

    val injector = mkInjector()
    val plan = injector.plan(PlannerInput(definition))

    val context = injector.produce(plan)
    val instantiated = context.get[TestTrait]("named-trait")
    assert(instantiated.isInstanceOf[TestTrait])
    assert(instantiated.dep != null)
  }

  "handle mixed sub-trait with protected autowires" in {
    import TraitCase2._

    val definition = new StaticModuleDef {
      stat[Trait3]
      stat[Trait2]
      stat[Trait1]
      stat[Dependency3]
      stat[Dependency2]
      stat[Dependency1]
    }

    val injector = mkInjector()
    val plan = injector.plan(PlannerInput(definition))

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
    import TraitCase2._

    val definition = new StaticModuleDef {
      make[Trait2].stat[Trait3]
      stat[Dependency3]
      stat[Dependency2]
      stat[Dependency1]
    }

    val injector = mkInjector()
    val plan = injector.plan(PlannerInput(definition))

    val context = injector.produce(plan)
    val instantiated3 = context.get[Trait2]
    assert(instantiated3.isInstanceOf[Trait2])
    assert(instantiated3.asInstanceOf[Trait3].prr() == "Hello World")
  }

  "support named bindings in macro traits" in {
    import TraitCase4._

    val definition = new StaticModuleDef {
      make[Dep].named("A").stat[DepA]
      make[Dep].named("B").stat[DepB]
      stat[Trait]
      stat[Trait1]
    }

    val injector = mkInjector()
    val plan = injector.plan(PlannerInput(definition))

    val context = injector.produce(plan)
    val instantiated = context.get[Trait]

    assert(instantiated.depA.isA)
    assert(!instantiated.depB.isA)

    val instantiated1 = context.get[Trait1]

    assert(instantiated1.depA.isA)
    assert(!instantiated1.depB.isA)
  }

  "override protected defs in macro traits" in {
    import TraitCase5._

    val definition = new StaticModuleDef {
      stat[TestTrait]
      stat[Dep]
    }

    val injector = mkInjector()
    val plan = injector.plan(PlannerInput(definition))

    val context = injector.produce(plan)
    val instantiated = context.get[TestTrait]

    assert(instantiated.rd == Dep().toString)
  }

  "handle AnyVals" in {
    import com.github.pshirshov.izumi.distage.fixtures.TraitCases.TraitCase6._

    val definition = new StaticModuleDef {
      stat[Dep]
      stat[AnyValDep]
      stat[TestTrait]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

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
