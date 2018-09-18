package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.definition.BootstrapModuleDef
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.planning.AssignableFromAutoSetHook
import com.github.pshirshov.izumi.distage.planning.gc.TracingGcModule
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.logstage.distage.LogstageModule
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink
import distage.{BootstrapModule, Injector, ModuleBase, Tag}


trait DistageTests {
  protected val resourceCollection: DistageResourceCollection = NullDistageResourceCollection

  protected def di[T: Tag](f: T => Any): Unit = {
    val providerMagnet: ProviderMagnet[Unit] = { x: T => f(x); () }
    di(providerMagnet)
  }

  protected def di(f: ProviderMagnet[Any]): Unit = {
    ctx(f.get.diKeys.toSet) {
      context =>
        try {
          context.run(f).discard()
        } finally {
          finalizeTest(context)
        }
    }
  }

  protected def ctx(roots: Set[DIKey])(f: Locator => Unit): Unit = {
    val injector = makeInjector(roots)
    val primaryModule = makeBindings()
    val plan = makeContext(injector, primaryModule)
    val finalPlan = refinePlan(injector, plan)
    val context = makeContext(injector, finalPlan)
    resourceCollection.processContext(context)
    f(context)
  }

  protected def finalizeTest(context: Locator): Unit = {
    context.run {
      closeables: Set[AutoCloseable] =>
        closeables.foreach(resourceCollection.close)
    }
  }

  protected def refinePlan(injector: Injector, plan: OrderedPlan): OrderedPlan = {
    val semi = plan.map(resourceCollection.transformPlanElement)
    val finalPlan = injector.finish(semi)
    finalPlan
  }

  protected def makeContext(injector: Injector, plan: OrderedPlan): Locator = {
    injector.produce(plan)
  }

  protected def makeContext(injector: Injector, primaryModule: ModuleBase): OrderedPlan = {

    val modules = Seq(
      primaryModule,
      new LogstageModule(IzLogger.simpleRouter(Log.Level.Debug, ConsoleSink.ColoredConsoleSink)),
    ) ++
      makeConfig().map(c => new ConfigModule(c)).toSeq

    val plan = injector.plan(modules.overrideLeft)
    plan
  }

  protected def makeInjector(roots: Set[DIKey]): Injector = {
    val allRoots = roots ++ Set(DIKey.get[Set[AutoCloseable]])
    val closeablesHook = new AssignableFromAutoSetHook[AutoCloseable]()
    val bootstrapModules = Seq[BootstrapModule](
      new TracingGcModule(allRoots),
      new BootstrapModuleDef {
        many[AutoCloseable]
        many[PlanningHook]
          .add(closeablesHook)
      },
    ).merge

    val injector = Injector.bootstrap(overrides = bootstrapModules)

    injector
  }

  protected def makeBindings(): ModuleBase

  protected def makeConfig(): Option[AppConfig] = None
}
