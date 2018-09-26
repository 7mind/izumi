package com.github.pshirshov.izumi.distage.testkit

import java.util.concurrent.atomic.AtomicBoolean

import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ConfigModule, SimpleLoggerConfigurator}
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
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.logstage.distage.LogstageModule
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink
import com.typesafe.config.ConfigFactory
import distage.{BootstrapModule, Injector, ModuleBase, Tag}
import org.scalatest.exceptions.TestCanceledException

import scala.util.Try


trait DistageTests {
  protected val resourceCollection: DistageResourceCollection = NullDistageResourceCollection
  protected val baseRouter: LogRouter = IzLogger.simpleRouter(Log.Level.Info, ConsoleSink.ColoredConsoleSink)

  protected def di[T: Tag](f: T => Any): Unit = {
    val providerMagnet: ProviderMagnet[Unit] = { x: T => f(x); () }
    di(providerMagnet)
  }

  protected def di(f: ProviderMagnet[Any]): Unit = {
    ctx(f.get.diKeys.toSet ++ suiteRoots) {
      context =>
        try {
          verifyTotalSuppression()
          beforeRun(context)
          verifyTotalSuppression()

          context.run(f).discard()
        } finally {
          finalizeTest(context)
        }
    }
  }

  private def verifyTotalSuppression(): Unit = {
    if (suppressAll.get()) {
      ignoreThisTest("The rest of this test suite has been suppressed")
    }
  }

  protected def suiteRoots: Set[DIKey] = Set.empty

  private val suppressAll = new AtomicBoolean(false)

  protected def suppressTheRestOfTestSuite(): Unit = {
    suppressAll.set(true)
  }

  protected def beforeRun(context: Locator): Unit = {
    context.discard()
  }

  protected def ignoreThisTest(cause: Throwable): Nothing = {
    ignoreThisTest(None, Some(cause))
  }

  protected def ignoreThisTest(message: String): Nothing = {
    ignoreThisTest(Some(message), None)
  }

  protected def ignoreThisTest(message: String, cause: Throwable): Nothing = {
    ignoreThisTest(Some(message), Some(cause))
  }

  protected def ignoreThisTest(message: Option[String] = None, cause: Option[Throwable] = None): Nothing = {
    throw new TestCanceledException(message, cause, failedCodeStackDepth = 0)
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

  protected def makeLogRouter(config: Option[AppConfig]): LogRouter = {
    val maybeLoggerConfig = for {
      appConfig <- config
      loggerConfig <- Try(appConfig.config.getConfig("logger")).toOption
    } yield {
      loggerConfig
    }

    maybeLoggerConfig match {
      case Some(value) =>
        new SimpleLoggerConfigurator(new IzLogger(baseRouter, Log.CustomContext.empty))
          .makeLogRouter(value, Log.Level.Info, json = false)

      case None =>
        baseRouter
    }
  }

  protected def makeContext(injector: Injector, primaryModule: ModuleBase): OrderedPlan = {
    val modules = Seq(
      primaryModule,
    )

    injector.plan(modules.overrideLeft)
  }

  protected def makeInjector(roots: Set[DIKey]): Injector = {
    val maybeConfig = makeConfig


    val allRoots = roots ++ Set(DIKey.get[Set[AutoCloseable]])
    val closeablesHook = new AssignableFromAutoSetHook[AutoCloseable]()
    val bsModule = new BootstrapModuleDef {
      many[AutoCloseable]
      many[PlanningHook]
        .add(closeablesHook)
    }

    val bootstrapModules = Seq[BootstrapModule](
      new TracingGcModule(allRoots),
      new LogstageModule(makeLogRouter(maybeConfig)),
      bsModule,
    ) ++
      maybeConfig.map(c => new ConfigModule(c, configOptions)).toSeq

    Injector.bootstrap(overrides = bootstrapModules.merge)
  }

  protected def makeBindings(): ModuleBase

  protected def makeConfig: Option[AppConfig] = {
    Some(AppConfig(ConfigFactory.parseResources("test-reference.conf").resolveWith(ConfigFactory.defaultOverrides())))
  }

  protected def configOptions: ConfigInjectionOptions = ConfigInjectionOptions()
}
