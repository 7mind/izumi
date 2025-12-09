package izumi.distage.injector

import distage.{BootstrapModuleDef, Injector, ModuleDef}
import izumi.distage.fixtures.SetCases.*
import izumi.distage.model.PlannerInput
import izumi.distage.model.planning.PlanningHook
import izumi.distage.planning.AutoSetHook
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.wordspec.AnyWordSpec

class AutoSetTest extends AnyWordSpec with MkInjector {

  "AutoSets preserve dependency order" in {
    import SetCase3.*

    val definition = new ModuleDef {
      make[ServiceA]
      make[ServiceB]
      make[ServiceC]
      make[ServiceD]
    }

    val injector = Injector[Identity](bootstrapOverrides = Seq(new BootstrapModuleDef {
      many[PlanningHook]
        .add(AutoSetHook[Ordered]("order")(weak = true))
    }))

    val autoset = injector.produce(PlannerInput.everything(definition)).unsafeGet().get[Set[Ordered]]("order")

    assert(autoset.size == 4)
    assert(autoset.toSeq == autoset.toSeq.sortBy(_.order))
  }

  "AutoSets collect instances with the same type but different implementations" in {
    val definition = new ModuleDef {
      make[Int].fromValue(1)
      make[Int].named("x").fromValue(2)
      many[Int]
        .named("nonauto")
        .addValue(3)
        .addValue(4)
        .addValue(5)
    }

    val injector = Injector[Identity](bootstrapOverrides = Seq(new BootstrapModuleDef {
      many[PlanningHook]
        .add(AutoSetHook[Int](weak = true))
    }))

    val autoset = injector.produce(PlannerInput.everything(definition)).unsafeGet().get[Set[Int]]

    assert(autoset == Set(1, 2, 3, 4, 5))
  }

  "AutoSet doc example" in {
    import distage.{AutoSetModule, BootstrapModule, ModuleDef, Injector, Identity}

    class PrintService(
      name: String
    ) {
      def start(): String = name
    }

    trait A
    class AImpl extends PrintService("A") with A
    class B(val a: A) extends PrintService("B")
    class C(val b: B) extends PrintService("C")

    def bootstrapModule: BootstrapModule = new AutoSetModule {
      register[PrintService](weak = false)
    }

    def appModule = new ModuleDef {
      make[A].from[AImpl]
      make[B]
      make[C]
    }

    val services: Set[PrintService] = Injector[Identity](bootstrapOverrides = Seq(bootstrapModule))
      .produceGet[Set[PrintService]](appModule)
      .unsafeGet()

    assert(services.size == 3)

    services.foreach(_.start())

    assert(services.toList.map(_.start()) == List("A", "B", "C"))

    def weakAutoSetModule: BootstrapModule = new AutoSetModule {
      register[PrintService](weak = true)
    }

    val servicesDepsOfB: Set[PrintService] = Injector[Identity](bootstrapOverrides = Seq(weakAutoSetModule))
      .produceRun(appModule) {
        (_: B, set: Set[PrintService]) =>
          set
      }

    assert(servicesDepsOfB.size == 2)

    servicesDepsOfB.foreach(_.start())

    val servicesDepsOfNothing: Set[PrintService] = Injector[Identity](bootstrapOverrides = Seq(weakAutoSetModule))
      .produceGet[Set[PrintService]](appModule)
      .unsafeGet()

    assert(servicesDepsOfNothing.isEmpty)
  }

}
