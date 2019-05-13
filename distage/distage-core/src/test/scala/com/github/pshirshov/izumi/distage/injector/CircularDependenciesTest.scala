package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.CircularCases._
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.PlannerInput
import com.github.pshirshov.izumi.distage.model.exceptions.{ProvisioningException, TraitInitializationFailedException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.InstantiationOp
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp.MakeProxy
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyDispatcher
import distage._
import org.scalatest.WordSpec

class CircularDependenciesTest extends WordSpec with MkInjector {

  "support circular dependencies" in {
    import CircularCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Circular2]
      make[Circular1]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)
    assert(context.get[Circular2].arg != null)
  }

  "support circular dependencies with final class implementations" in {
    import CircularCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Circular2].from[Circular2Impl]
      make[Circular1].from[Circular1Impl]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)
    assert(context.get[Circular2].arg != null)
  }

  "support trait initialization" in {
    import CircularCase2._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[CircularBad1]
      make[CircularBad2]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val exc = intercept[ProvisioningException] {
      injector.produceUnsafe(plan)
    }

    assert(exc.getSuppressed.head.isInstanceOf[TraitInitializationFailedException])
    assert(exc.getSuppressed.head.getCause.isInstanceOf[RuntimeException])
  }

  "support circular dependencies in providers" in {
    import CircularCase1._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Circular2].from { c: Circular1 => new Circular2(c) }
      make[Circular1].from { c: Circular2 =>
        val a = new Circular1 {
          override val arg: Circular2 = c
        }
        a
      }
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)
    assert(context.get[Circular2].arg != null)
  }

  "support complex circular dependencies" in {
    import CircularCase2._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Circular3]
      make[Circular1]
      make[Circular2]
      make[Circular5]
      make[Circular4]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    assert(plan.topology.dependencies.tree(DIKey.get[Circular1], Some(3)).children.size == 1)
    assert(plan.topology.dependees.tree(DIKey.get[Circular1], Some(3)).children.size == 2)

    val context = injector.produceUnsafe(plan)
    val c3 = context.get[Circular3]
    val traitArg = c3.arg

    assert(traitArg != null && traitArg.isInstanceOf[Circular4])
    assert(c3.method == 2L)
    assert(traitArg.testVal == 1)
    assert(context.instances.nonEmpty)
    assert(context.get[Circular4].factoryFun(context.get[Circular4], context.get[Circular5]) != null)
  }

  "Supports self-referencing circulars" in {
    import CircularCase3._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[SelfReference]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val instance = context.get[SelfReference]

    assert(instance eq instance.self)
  }

  "Support self-referencing provider" in {
    import CircularCase3._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[SelfReference].from {
        self: SelfReference =>
          new SelfReference(self)
      }
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val instance = context.get[SelfReference]

    assert(instance eq instance.self)
  }

  "Support by-name self-referencing circulars" in {
    import CircularCase3._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[ByNameSelfReference]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val instance = context.get[ByNameSelfReference]

    assert(instance eq instance.self)
  }

  "Locator.instances returns instances in the order they were created in" in {
    import CircularCase2._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Circular3]
      make[Circular1]
      make[Circular2]
      make[Circular5]
      make[Circular4]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    val planTypes: Seq[SafeType] = plan.steps
      .collect {
        case i: InstantiationOp => i
        case i: MakeProxy => i
      }
      .map(_.target.tpe)
    val internalArtifacts = Set(SafeType.get[ProxyDispatcher], SafeType.get[LocatorRef])
    val instanceTypes: Seq[SafeType] = context.instances.map(_.key.tpe)
      .filterNot(internalArtifacts.contains) // remove internal artifacts: proxy stuff, locator ref

    assert(instanceTypes == planTypes)

    // whitebox test: ensure that plan ops are in a non-lazy collection
    assert(plan.steps.getClass == classOf[Vector[_]])
  }

  "support by-name circular dependencies" in {
    import ByNameCycle._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Circular2]
      make[Circular1]
      make[Int].fromValue(1)
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)

    assert(context.get[Circular1].isInstanceOf[Circular1])
    assert(context.get[Circular2].isInstanceOf[Circular2])

    assert(context.get[Circular1].test.isInstanceOf[Circular2])
    assert(context.get[Circular2].test.isInstanceOf[Circular1])
  }

  "support generic circular dependencies when generics are erased by type-erasure" in {
    import CircularCase5._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[ErasedCircular[Dependency]]
      make[PhantomDependency[Dependency]]
    })

    val injector = mkInjector()
    val context = injector.produceUnsafe(definition)

    assert(context.get[ErasedCircular[Dependency]] != null)
    assert(context.get[PhantomDependency[Dependency]] != null)

    assert(context.get[ErasedCircular[Dependency]].dep eq context.get[PhantomDependency[Dependency]])
    assert(context.get[PhantomDependency[Dependency]] eq context.get[ErasedCircular[Dependency]].dep)
  }

  "support fully generic circular dependencies" in {
    import CircularCase5._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[GenericCircular[Dependency]]
      make[Dependency]
    })

    val injector = mkInjector()
    val context = injector.produceUnsafe(definition)

    assert(context.get[Dependency] != null)
    assert(context.get[GenericCircular[Dependency]] != null)

    assert(context.get[Dependency] eq context.get[GenericCircular[Dependency]].dep)
    assert(context.get[GenericCircular[Dependency]] eq context.get[Dependency].dep)
  }

  "support named circular dependencies" in {
    import CircularCase4._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[IdTypeCircular]
      make[IdParamCircular]
      make[Dependency[IdTypeCircular]].named("special")
      make[Dependency[IdParamCircular]].named("special")
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produceUnsafe(plan)

    assert(context.get[IdTypeCircular] != null)
    assert(context.get[IdParamCircular] != null)

    assert(context.get[IdParamCircular] eq context.get[Dependency[IdParamCircular]]("special").dep)
    assert(context.get[IdTypeCircular] eq context.get[Dependency[IdTypeCircular]]("special").dep)
  }

  "support type refinements in circular dependencies" in {
    import CircularCase6._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Dependency {def dep: RefinedCircular}].from[RealDependency]
      make[RefinedCircular]
    })

    val injector = mkInjector()
    val context = injector.produceUnsafe(definition)

    assert(context.get[Dependency {def dep: RefinedCircular}] != null)
    assert(context.get[RefinedCircular] != null)

    assert(context.get[RefinedCircular] eq context.get[Dependency {def dep: RefinedCircular}].dep)
    assert(context.get[Dependency {def dep: RefinedCircular}] eq context.get[RefinedCircular].dep)
  }

}
