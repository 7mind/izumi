package izumi.distage.injector

import distage.{DIKey, Id, Injector, Module}
import izumi.distage.fixtures.BasicCases.*
import izumi.distage.fixtures.SetCases.SetCase1
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.StandardAxis.{Mode, Repo}
import izumi.distage.model.definition.{Activation, Axis, BootstrapModuleDef, ModuleDef}
import izumi.distage.model.exceptions.planning.InjectorFailed
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

    val injector1 = Injector[Identity](bootstrapActivation = Activation(Repo -> Repo.Prod), overrides = Seq(bsDefinition))
    val context1 = injector1.produce(PlannerInput(appDefinition, Activation.empty, Roots.Everything)).unsafeGet()

    assert(context1.get[JustTrait].isInstanceOf[Impl1])
    assert(!context1.get[JustTrait].isInstanceOf[Impl0])

    val injector2 = Injector[Identity](bootstrapActivation = Activation(Repo -> Repo.Dummy), overrides = Seq(bsDefinition))
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
      .produceGet[JustTrait](definition, Activation(Repo -> Repo.Prod))
      .unsafeGet()
    assert(instance.isInstanceOf[Impl1])
  }

  "should choose implementation with no axis as a default if there's both an implementation with no axis and the opposite choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    val instance = mkInjector()
      .produceGet[JustTrait](definition, Activation(Repo -> Repo.Dummy))
      .unsafeGet()
    assert(instance.isInstanceOf[Impl0])
  }

  "should raise conflict if there's both an implementation with no axis and a choice, but no choice is set" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    assertThrows[InjectorFailed] {
      mkInjector()
        .produceGet[JustTrait](definition, Activation())
        .unsafeGet()
        .isInstanceOf[Impl1]
    }
  }

  "should raise conflict if there's both an implementation with no axis and an unset choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].from[Impl0]
      make[JustTrait].tagged(Repo.Prod).from[Impl1]
    }

    assertThrows[InjectorFailed] {
      mkInjector()
        .produceGet[JustTrait](definition, Activation(Mode -> Mode.Prod))
        .unsafeGet()
    }
  }

  "should raise conflict if there's both an implementation with no axis and one with more choices than current choice" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].from[Impl0]
      make[JustTrait].tagged(Repo.Prod, Mode.Prod).from[Impl1]
    }

    assertThrows[InjectorFailed] {
      mkInjector()
        .produceGet[JustTrait](definition, Activation(Mode -> Mode.Prod))
        .unsafeGet()
    }
  }

  "a sole existing implementation with an axis should always be selected as long as there are no opposite axis choices" in {
    import BasicCase1._

    val definition = new ModuleDef {
      make[JustTrait].tagged(Repo.Prod, Mode.Prod).from[Impl1]
    }

    assert(mkInjector().produceGet[JustTrait](definition, Activation(Repo -> Repo.Prod)).unsafeGet().isInstanceOf[Impl1])
    assert(mkInjector().produceGet[JustTrait](definition, Activation(Mode -> Mode.Prod)).unsafeGet().isInstanceOf[Impl1])
    assert(mkInjector().produceGet[JustTrait](definition, Activation()).unsafeGet().isInstanceOf[Impl1])
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

    intercept[InjectorFailed] {
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

    intercept[InjectorFailed] {
      mkInjector()
        .produce(PlannerInput(definition, Activation(), Roots(DIKey[Set[SetTrait]])))
        .unsafeGet()
        .get[Set[SetTrait]]
    }
  }

  "exclude set elements with unselected unsaturated axis" in {
    import SetCase1._

    val baseDef = new ModuleDef {
      many[SetTrait]
        .add[SetImpl1].tagged(Repo.Prod)
    }

    val definitionTodo = baseDef ++ new ModuleDef {
      make[SetTrait].todo.tagged(Repo.Dummy)
    }

    val instance1 = mkInjector()
      .produce(PlannerInput(definitionTodo, Activation(Repo.Dummy), Roots(DIKey[Set[SetTrait]])))
      .unsafeGet()
      .get[Set[SetTrait]]

    assert(instance1.isEmpty)

    val instance2 = mkInjector()
      .produce(PlannerInput(definitionTodo, Activation(Repo.Prod), Roots(DIKey[Set[SetTrait]])))
      .unsafeGet()
      .get[Set[SetTrait]]

    assert(instance2.size == 1)

    val instance3 = mkInjector()
      .produce(PlannerInput(baseDef, Activation(Repo.Prod), Roots(DIKey[Set[SetTrait]])))
      .unsafeGet()
      .get[Set[SetTrait]]

    assert(instance3.size == 1)

    val instance4 = mkInjector()
      .produce(PlannerInput(baseDef, Activation(Repo.Dummy), Roots(DIKey[Set[SetTrait]])))
      .unsafeGet()
      .get[Set[SetTrait]]

    assert(instance4.isEmpty)
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

  "choose according to specificity rules" in {
    object Style extends Axis {
      case object AllCaps extends AxisChoiceDef
      case object Normal extends AxisChoiceDef
    }

    sealed trait Color
    case object RED extends Color
    case object Blue extends Color
    case object Green extends Color

    def DefaultsModule = new ModuleDef {
      make[Color].from(Green)
      make[Color].tagged(Style.AllCaps).from(RED)
    }

    assert(
      Injector().produceRun(DefaultsModule, Activation(Style -> Style.AllCaps))(identity(_: Color))
      == RED
    )

    assert(
      Injector().produceRun(DefaultsModule, Activation(Style -> Style.Normal))(identity(_: Color))
      == Green
    )

    assertThrows[InjectorFailed](Injector().produceRun(DefaultsModule, Activation.empty)(println(_: Color)))

    def SpecificityModule = new ModuleDef {
      make[Color].tagged(Mode.Test).from(Blue)
      make[Color].tagged(Mode.Prod).from(Green)
      make[Color].tagged(Mode.Prod, Style.AllCaps).from(RED)
    }

    assert(
      Injector().produceRun(SpecificityModule, Activation(Mode -> Mode.Prod, Style -> Style.AllCaps))(identity(_: Color))
      == RED
    )

    assert(
      Injector().produceRun(SpecificityModule, Activation(Mode -> Mode.Test, Style -> Style.AllCaps))(identity(_: Color))
      == Blue
    )

    assert(
      Injector().produceRun(SpecificityModule, Activation(Mode -> Mode.Prod, Style -> Style.Normal))(identity(_: Color))
      == Green
    )

    assert(
      Injector().produceRun(SpecificityModule, Activation(Mode -> Mode.Test))(identity(_: Color))
      == Blue
    )

    assertThrows[InjectorFailed](Injector().produceRun(SpecificityModule, Activation(Style -> Style.Normal))(identity(_: Color)))
  }

}
