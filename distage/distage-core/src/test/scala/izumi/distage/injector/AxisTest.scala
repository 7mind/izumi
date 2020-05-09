package izumi.distage.injector

import distage.{DIKey, Injector, Module}
import izumi.distage.fixtures.BasicCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.model.definition.{Activation, BootstrapModuleDef, ModuleDef}
import izumi.distage.model.plan.GCMode
import org.scalatest.wordspec.AnyWordSpec

class AxisTest extends AnyWordSpec with MkInjector {

  "choose between dependency implementations" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].tagged(Repo.Dummy).from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    val injector1 = Injector()
    val context1 = injector1.produce(PlannerInput(definition, Activation(Repo -> Repo.Prod), GCMode(DIKey.get[JustTrait]))).unsafeGet()

    assert(context1.get[JustTrait].isInstanceOf[Impl1])
    assert(!context1.get[JustTrait].isInstanceOf[Impl0])

    val injector2 = Injector()
    val context2 = injector2.produce(PlannerInput(definition, Activation(Repo -> Repo.Dummy), GCMode(DIKey.get[JustTrait]))).unsafeGet()

    assert(context2.get[JustTrait].isInstanceOf[Impl0])
    assert(!context2.get[JustTrait].isInstanceOf[Impl1])
  }

  "choose between dependency implementations in bootstrap plan" in {
    import BasicCase1._

    val bsDefinition = new BootstrapModuleDef {
      make[JustTrait].tagged(Repo.Dummy).from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }
    val appDefinition = Module.empty

    val injector1 = Injector(Activation(Repo -> Repo.Prod), bsDefinition)
    val context1 = injector1.produce(PlannerInput(appDefinition, Activation.empty, GCMode.NoGC)).unsafeGet()

    assert(context1.get[JustTrait].isInstanceOf[Impl1])
    assert(!context1.get[JustTrait].isInstanceOf[Impl0])

    val injector2 = Injector(Activation(Repo -> Repo.Dummy), bsDefinition)
    val context2 = injector2.produce(PlannerInput(appDefinition, Activation.empty, GCMode.NoGC)).unsafeGet()

    assert(context2.get[JustTrait].isInstanceOf[Impl0])
    assert(!context2.get[JustTrait].isInstanceOf[Impl1])
  }

  "remove the sole existing implementation if the opposite axis is the current choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].tagged(Repo.Dummy).from[Impl0]
    }

    val context = Injector().produce(PlannerInput(definition, Activation(Repo -> Repo.Prod), GCMode.NoGC)).unsafeGet()

    assert(context.find[JustTrait].isEmpty)
  }

  "report conflict if there's both an implementation with no axis and the current choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    assert(Injector().produce(PlannerInput(definition, Activation(Repo -> Repo.Prod), GCMode(DIKey.get[JustTrait]))).unsafeGet().get[JustTrait].isInstanceOf[Impl1])
  }

  "report conflict if there's both an implementation with no axis and the opposite choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].from[Impl0]
      make[JustTrait].tagged(Repo.Dummy).from[Impl1]
    }

    assert(Injector().produce(PlannerInput(definition, Activation(Repo -> Repo.Prod), GCMode(DIKey.get[JustTrait]))).unsafeGet().get[JustTrait].isInstanceOf[Impl0])
  }

}
