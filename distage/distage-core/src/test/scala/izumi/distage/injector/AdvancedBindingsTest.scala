package izumi.distage.injector

import distage.{Injector, ModuleDef}
import izumi.distage.fixtures.BasicCases.BasicCase1
import izumi.distage.fixtures.SetCases.{SetCase2, SetCase4}
import izumi.distage.model.PlannerInput
import izumi.distage.model.exceptions.runtime.TODOBindingException
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Try

class AdvancedBindingsTest extends AnyWordSpec with MkInjector {

  "Support TODO bindings" in {
    import BasicCase1.*

    val injector = mkInjector()

    val def1 = PlannerInput.everything(new ModuleDef {
      todo[TestDependency0]
    })
    val def2 = PlannerInput.everything(new ModuleDef {
      make[TestDependency0].todo
    })
    val def3 = PlannerInput.everything(new ModuleDef {
      make[TestDependency0].named("fug").todo
    })

    val plan1 = injector.planUnsafe(def1)
    val plan2 = injector.planUnsafe(def2)
    val plan3 = injector.planUnsafe(def3)

    assert(Try(injector.produce(plan1).unsafeGet()).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
    assert(Try(injector.produce(plan2).unsafeGet()).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
    assert(Try(injector.produce(plan3).unsafeGet()).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
  }

  "Sets are being extended when injector inheritance happens (https://github.com/7mind/izumi/issues/330)" in {
    import SetCase4.*

    val definitionParent = PlannerInput.everything(new ModuleDef {
      many[Service]
        .add[Service1]
    })
    val definitionSub = PlannerInput.everything(new ModuleDef {
      many[Service]
        .add[Service2]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definitionParent)
    val context = injector.produce(plan).unsafeGet()

    val subInjector = Injector.inherit[Identity](context)
    val planSub = subInjector.planUnsafe(definitionSub)
    val contextSub = subInjector.produce(planSub).unsafeGet()

    val set = contextSub.get[Set[Service]]
    assert(set.size == 2)
  }

  "Set element references are the same as their referees" in {
    import SetCase2.*

    val definition = PlannerInput.everything(new ModuleDef {
      make[Service1]

      many[Service]
        .ref[Service1]
    })

    val injector = mkInjector()
    val plan = injector.planUnsafe(definition)

    val context = injector.produce(plan).unsafeGet()
    val svc = context.get[Service1]
    val set = context.get[Set[Service]]
    assert(set.head eq svc)
  }
}
