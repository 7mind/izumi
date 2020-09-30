package izumi.distage.injector

import distage.{DIKey, Id, Injector, Module}
import izumi.distage.fixtures.BasicCases._
import izumi.distage.fixtures.SetCases.SetCase1
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.StandardAxis.{Mode, Repo}
import izumi.distage.model.definition.{Activation, BootstrapModuleDef, ModuleDef}
import izumi.distage.model.exceptions.BadSetAxis
import izumi.distage.model.plan.Roots
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class AxisTest extends AnyWordSpec with MkInjector {

  "choose between dependency implementations" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].tagged(Repo.Dummy).from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    val injector1 = mkInjector()
    val context1 = injector1.produce(PlannerInput(definition, Activation(Repo -> Repo.Prod), Roots(DIKey.get[JustTrait]))).unsafeGet()

    assert(context1.get[JustTrait].isInstanceOf[Impl1])
    assert(!context1.get[JustTrait].isInstanceOf[Impl0])

    val injector2 = mkInjector()
    val context2 = injector2.produce(PlannerInput(definition, Activation(Repo -> Repo.Dummy), Roots(DIKey.get[JustTrait]))).unsafeGet()

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

    val injector1 = Injector.withBootstrapActivation[Identity](Activation(Repo -> Repo.Prod), bsDefinition)
    val context1 = injector1.produce(PlannerInput(appDefinition, Activation.empty, Roots.Everything)).unsafeGet()

    assert(context1.get[JustTrait].isInstanceOf[Impl1])
    assert(!context1.get[JustTrait].isInstanceOf[Impl0])

    val injector2 = Injector.withBootstrapActivation[Identity](Activation(Repo -> Repo.Dummy), bsDefinition)
    val context2 = injector2.produce(PlannerInput(appDefinition, Activation.empty, Roots.Everything)).unsafeGet()

    assert(context2.get[JustTrait].isInstanceOf[Impl0])
    assert(!context2.get[JustTrait].isInstanceOf[Impl1])
  }

  "remove the sole existing implementation if the opposite axis is the current choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].tagged(Repo.Dummy).from[Impl0]
    }

    val context = mkInjector()
      .produce(PlannerInput(definition, Activation(Repo -> Repo.Prod), Roots.Everything))
      .unsafeGet()

    assert(context.find[JustTrait].isEmpty)
  }

  "should choose implementation with the current axis choice if there's both an implementation with no axis and the current choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    val instance = mkInjector()
      .produce(PlannerInput(definition, Activation(Repo -> Repo.Prod), Roots(DIKey.get[JustTrait])))
      .unsafeGet()
      .get[JustTrait]
    assert(instance.isInstanceOf[Impl1])
  }

  "should choose implementation with no axis as a default if there's both an implementation with no axis and the opposite choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    val instance = mkInjector()
      .produce(PlannerInput(definition, Activation(Repo -> Repo.Dummy), Roots(DIKey[JustTrait])))
      .unsafeGet()
      .get[JustTrait]
    assert(instance.isInstanceOf[Impl0])
  }

  "#1221: choose set elements with expected axis tag" in {
    import SetCase1._

    val definition = new ModuleDef {
      many[SetTrait]
        .add[SetImpl1].tagged(Repo.Prod)
        .add[SetImpl2].tagged(Repo.Dummy)
        .add[SetImpl5]
    }

    val instance = mkInjector()
      .produce(PlannerInput(definition, Activation(Repo -> Repo.Dummy), Roots(DIKey[Set[SetTrait]])))
      .unsafeGet()
      .get[Set[SetTrait]]

    assert(instance.size == 2)
    assert(!instance.exists(_.isInstanceOf[SetImpl1]))
    assert(instance.exists(_.isInstanceOf[SetImpl2]))
    assert(instance.exists(_.isInstanceOf[SetImpl5]))
  }

  "#1221: throw on elements with at least one undefined axis when element is unique" in {
    import SetCase1._

    val definition = new ModuleDef {
      many[SetTrait]
        .add[SetImpl1].tagged(Repo.Prod)
        .add[SetImpl5]
    }

    intercept[BadSetAxis] {
      mkInjector()
        .produce(PlannerInput(definition, Activation(), Roots(DIKey[Set[SetTrait]])))
        .unsafeGet()
        .get[Set[SetTrait]]
    }
  }

  "#1221: throw on elements with at least one undefined axis" in {
    import SetCase1._

    val definition = new ModuleDef {
      many[SetTrait]
        .add[SetImpl1].tagged(Repo.Prod)
        .add[SetImpl2].tagged(Repo.Dummy)
        .add[SetImpl3].tagged(Repo.Dummy, Mode.Test)
        .add[SetImpl5]
    }

    intercept[BadSetAxis] {
      mkInjector()
        .produce(PlannerInput(definition, Activation(), Roots(DIKey[Set[SetTrait]])))
        .unsafeGet()
        .get[Set[SetTrait]]
    }
  }

  "work correctly with named Unit" in {
    class X(u: Unit @Id("x")) { val x: Unit = u }

    val definition = new ModuleDef {
      make[Unit].named("x").tagged(Repo.Dummy).fromValue(())
      make[Unit].named("x").tagged(Repo.Prod).fromValue(())
      make[X]
    }

    val instance = mkInjector()
      .produce(PlannerInput(definition, Activation(Repo -> Repo.Dummy), Roots(DIKey[X])))
      .unsafeGet()
      .get[X]

    assert(definition.bindings.size == 3)
    assert(instance.x ne null)
  }
}
