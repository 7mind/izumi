package izumi.distage.injector

import distage.{DIKey, Injector}
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.model.definition.{Activation, ModuleDef}
import izumi.distage.model.plan.GCMode
import org.scalatest.wordspec.AnyWordSpec

class AxisTest extends AnyWordSpec with MkInjector {

  "choose between dependency implementations" in {
    import izumi.distage.fixtures.BasicCases.BasicCase1._
    val definition = new ModuleDef {
      make[JustTrait].tagged(Repo.Dummy).from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    val injector1 = Injector(Activation(Repo -> Repo.Prod))
    val context1 = injector1.produce(PlannerInput(definition, GCMode(DIKey.get[JustTrait]))).unsafeGet()

    assert(context1.get[JustTrait].isInstanceOf[Impl1])
    assert(!context1.get[JustTrait].isInstanceOf[Impl0])

    val injector2 = Injector(Activation(Repo -> Repo.Dummy))
    val context2 = injector2.produce(PlannerInput(definition, GCMode(DIKey.get[JustTrait]))).unsafeGet()

    assert(context2.get[JustTrait].isInstanceOf[Impl0])
    assert(!context2.get[JustTrait].isInstanceOf[Impl1])
  }

}
