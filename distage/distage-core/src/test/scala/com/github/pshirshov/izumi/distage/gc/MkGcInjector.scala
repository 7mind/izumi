package com.github.pshirshov.izumi.distage.gc

import distage.{AutoSetModule, Injector, Locator, OrderedPlan}

trait MkGcInjector {
  def mkInjector(): Injector = {
    Injector(AutoSetModule().register[AutoCloseable])
  }

  implicit class InjectorExt(private val injector: Injector) {
    def fproduce(plan: OrderedPlan): Locator = {
      val updated = injector.finish(plan.toSemi)
      injector.produceUnsafe(updated)
    }
  }
}
