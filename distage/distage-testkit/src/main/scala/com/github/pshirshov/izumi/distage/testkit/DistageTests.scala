package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.{Locator, reflection}
import com.github.pshirshov.izumi.distage.planning.gc.TracingGcModule
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.logstage.distage.LogstageModule
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink
import distage.{BootstrapModule, Injector, ModuleBase, Tag}

trait DistageTests {
  protected def di[T: Tag](f: T => Any): Unit = {
    val providerMagnet: ProviderMagnet[Unit] = { x: T => f(x); () }
    di(providerMagnet)
  }

  protected def di(f: ProviderMagnet[Unit]): Unit = {
    val injector = makeInjector(f)
    val primaryModule = makeBindings()
    val plan = makeContext(injector, primaryModule)
    val context = makeContext(injector, plan)
    context.run(f)
  }

  protected def makeContext(injector: distage.Injector, plan: OrderedPlan): Locator = {
    injector.produce(plan)
  }

  protected def makeContext(injector: Injector, primaryModule: ModuleBase): OrderedPlan = {
    val plan = injector.plan(
      Seq(primaryModule,
        new LogstageModule(IzLogger.simpleRouter(Log.Level.Debug, ConsoleSink.ColoredConsoleSink))
      ).overrideLeft
    )
    plan
  }

  protected def makeInjector(f: ProviderMagnet[Unit]): distage.Injector = {
    val roots: Set[reflection.universe.RuntimeDIUniverse.DIKey] = f.get.diKeys.toSet
    val injector = Injector
      .bootstrap(overrides = Seq[BootstrapModule](new TracingGcModule(roots)).merge)
    injector
  }

  protected def makeBindings(): ModuleBase
}


