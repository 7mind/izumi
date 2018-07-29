package com.github.pshirshov.izumi.distage.injector

import com.github.pshirshov.izumi.distage.fixtures.CircularCases._
import com.github.pshirshov.izumi.distage.model.exceptions.{ProvisioningException, TraitInitializationFailedException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.InstantiationOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyDispatcher
import distage._
import org.scalatest.WordSpec

import scala.util.Try

class CircularDependenciesTest extends WordSpec with MkInjector {

  "support circular dependencies" in {
    import CircularCase1._

    val definition: ModuleBase = new ModuleDef {
      make[Circular2]
      make[Circular1]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)
    assert(context.get[Circular2].arg != null)
  }

  "support trait initialization" in {
    import CircularCase2._

    val definition: ModuleBase = new ModuleDef {
      make[CircularBad1]
      make[CircularBad2]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val exc = intercept[ProvisioningException] {
      injector.produce(plan)
    }
    assert(exc.getCause.isInstanceOf[TraitInitializationFailedException])
    assert(exc.getCause.getCause.isInstanceOf[RuntimeException])
  }

  "support circular dependencies in providers" in {
    import CircularCase1._

    val definition: ModuleBase = new ModuleDef {
      make[Circular2].from { c: Circular1 => new Circular2(c) }
      make[Circular1].from { c: Circular2 => new Circular1 {
        override val arg: Circular2 = c
      }
      }
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)
    assert(context.get[Circular2].arg != null)
  }

  "support complex circular dependencies" in {
    import CircularCase2._

    val definition: ModuleBase = new ModuleDef {
      make[Circular3]
      make[Circular1]
      make[Circular2]
      make[Circular5]
      make[Circular4]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    assert(plan.topology.dependencies.tree(DIKey.get[Circular1], Some(3)).children.size == 1)
    assert(plan.topology.dependees.tree(DIKey.get[Circular1], Some(3)).children.size == 2)

    val context = injector.produce(plan)
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

    val definition = new ModuleDef {
      make[SelfReference]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val instance = context.get[SelfReference]

    assert(instance eq instance.self)
  }

  "Progression test: Does not yet support by-name self-referencing circulars" in {
    val fail = Try {
      import CircularCase3._

      val definition = new ModuleDef {
        make[ByNameSelfReference]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instance = context.get[ByNameSelfReference]

      assert(instance eq instance.self)
    }

    assert(fail.isFailure)
  }

  "Locator.instances returns instances in the order they were created in" in {
    import CircularCase2._

    val definition: ModuleBase = new ModuleDef {
      make[Circular3]
      make[Circular1]
      make[Circular2]
      make[Circular5]
      make[Circular4]
    }

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan)

    val planTypes: Seq[SafeType] = plan.steps.collect { case i: InstantiationOp => i }.map(_.target.tpe)
    val instanceTypes: Seq[SafeType] = context.instances.map(_.key.tpe)
      .filter(_ != SafeType.get[ProxyDispatcher]) // remove artifacts of proxy generation

    assert(instanceTypes == planTypes)

    info("whitebox test: ensure that plan ops are in a non-lazy collection")
    assert(plan.steps.getClass == classOf[Vector[_]])
  }

}
