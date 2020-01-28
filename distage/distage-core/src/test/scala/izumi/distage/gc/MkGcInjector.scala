package izumi.distage.gc

import distage.{AutoSetModule, Injector, Locator, OrderedPlan}

trait MkGcInjector {
  def mkInjector(): Injector = {
    Injector(AutoSetModule().register[AutoCloseable])
  }
  def mkNoCglibInjector(): Injector = {
    Injector.NoProxies(AutoSetModule().register[AutoCloseable])
  }

  implicit class InjectorExt(injector: Injector) {
    def finishProduce(plan: OrderedPlan): Locator = {
      val updated = injector.finish(plan.toSemi)
      injector.produce(updated).unsafeGet()
    }
  }
}
