package com.github.pshirshov.izumi.distage.testkit

import java.util.concurrent.atomic.AtomicBoolean

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ConfigModule, SimpleLoggerConfigurator}
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.Locator.LocatorRef
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, ImplDef, Module}
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.planning.AssignableFromAutoSetHook
import com.github.pshirshov.izumi.distage.planning.gc.TracingGcModule
import com.github.pshirshov.izumi.distage.roles.launcher.RoleStarterImpl
import com.github.pshirshov.izumi.distage.roles.roles.{RoleComponent, RoleService, RoleStarter}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import com.github.pshirshov.izumi.logstage.api.routing.ConfigurableLogRouter
import com.github.pshirshov.izumi.logstage.api.{IzLogger, Log}
import com.github.pshirshov.izumi.logstage.distage.LogstageModule
import com.github.pshirshov.izumi.logstage.sink.ConsoleSink
import com.typesafe.config.ConfigFactory
import distage.{BootstrapModule, Injector, ModuleBase, Tag}
import org.scalatest.exceptions.TestCanceledException

import scala.util.Try

trait DistageTests {
  protected val resourceCollection: DistageResourceCollection = NullDistageResourceCollection
  protected val baseRouter: LogRouter = ConfigurableLogRouter(Log.Level.Info, ConsoleSink.ColoredConsoleSink)

  protected def di[T: Tag](f: T => Any): Unit = {
    val providerMagnet: ProviderMagnet[Unit] = { x: T => f(x); () }
    di(providerMagnet)
  }

  protected def di(f: ProviderMagnet[Any]): Unit = {
    ctx(f.get.diKeys.toSet ++ suiteRoots) {
      (context, roleStarter) =>
        try {
          verifyTotalSuppression()

          beforeRun(context, roleStarter)
          verifyTotalSuppression()

          startTestResources(context, roleStarter)
          verifyTotalSuppression()

          context.run(f).discard()
        } finally {
          roleStarter.stop()
          finalizeTest(context, roleStarter)
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

  /** You can override this to e.g. skip test when certain external dependencies are not available **/
  protected def beforeRun(context: Locator, roleStarter: RoleStarter): Unit = {
    context.discard
    roleStarter.discard
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

  /** You can override this to e.g. skip test on specific initialization failure (port unavailable, etc) **/
  protected def provisionExceptionHandler(throwable: Throwable): Locator = {
    throw throwable
  }

  protected def ctx(roots: Set[DIKey])(f: (Locator, RoleStarter) => Unit): Unit = {
    val injector = makeInjector(roots)
    val primaryModule = makeBindings
    val finalModule = refineBindings(roots, primaryModule)
    val plan = makePlan(injector, finalModule)
    val finalPlan = refinePlan(injector, plan)

    val context = try {
      makeContext(injector, finalPlan)
    } catch {
      case t: Throwable =>
        provisionExceptionHandler(t)
    }

    val roleStarter = makeRoleStarter(
      context.find[Set[RoleService]].getOrElse(Set.empty)
      , context.find[Set[RoleComponent]].getOrElse(Set.empty)
      , context.find[Set[AutoCloseable]].getOrElse(Set.empty)
      , context.find[IzLogger].getOrElse(IzLogger.NullLogger)
    )

    resourceCollection.processContext(context)

    f(context, roleStarter)
  }

  protected def startTestResources(context: Locator, roleStarter: RoleStarter): Unit = {
    context.discard

    roleStarter.start()
  }

  protected def finalizeTest(context: Locator, roleStarter: RoleStarter): Unit = {
    context.discard

    roleStarter.stop()
  }

  protected def refinePlan(injector: Injector, plan: OrderedPlan): OrderedPlan = {
    val semi = plan.map(resourceCollection.transformPlanElement)
    val finalPlan = injector.finish(semi)
    finalPlan
  }

  protected def refineBootstrapModules(modules: Seq[BootstrapModule]): Seq[BootstrapModule] = {
    modules
  }

  /** Override this to disable instantiation of fixture parameters that aren't bound in `makeBindings` */
  protected def refineBindings(roots: Set[DIKey], primaryModule: ModuleBase): ModuleBase = {
    val paramsModule = Module.make {
      (roots - DIKey.get[LocatorRef]).map {
        key =>
          SingletonBinding(key, ImplDef.TypeImpl(key.tpe))
      }
    }

    paramsModule overridenBy primaryModule
  }

  protected def makeRoleStarter(services: Set[RoleService]
                                , components: Set[RoleComponent]
                                , closeables: Set[AutoCloseable]
                                , logger: IzLogger): RoleStarter = {
    new RoleStarterImpl(services, components, closeables, logger)
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

  protected def makePlan(injector: Injector, primaryModule: ModuleBase): OrderedPlan = {
    val modules = Seq(
      primaryModule
    )

    injector.plan(modules.overrideLeft)
  }

  protected def makeContext(injector: Injector, plan: OrderedPlan): Locator = {
    injector.produce(plan)
  }

  protected def makeInjector(roots: Set[DIKey]): Injector = {
    val maybeConfig = makeConfig

    val roleStarterBootstrapModule = makeRoleStarterBootstrapModule

    val bootstrapModules = Seq[BootstrapModule](
      new TracingGcModule(roots),
      new LogstageModule(makeLogRouter(maybeConfig)),
      roleStarterBootstrapModule,
    ) ++
      maybeConfig.map(c => new ConfigModule(c, configOptions)).toSeq

    val finalModules = refineBootstrapModules(bootstrapModules)

    Injector.bootstrap(overrides = finalModules.merge)
  }

  protected def makeRoleStarterBootstrapModule: BootstrapModule = {
    val servicesHook = new AssignableFromAutoSetHook[RoleService]
    val closeablesHook = new AssignableFromAutoSetHook[AutoCloseable]
    val componentsHook = new AssignableFromAutoSetHook[RoleComponent]

    new BootstrapModuleDef {
      many[RoleService]
      many[AutoCloseable]
      many[RoleComponent]

      many[PlanningHook]
        .add(servicesHook)
        .add(closeablesHook)
        .add(componentsHook)
    }
  }

  protected def makeBindings: ModuleBase

  protected def makeConfig: Option[AppConfig] = {
    val pname = s"${this.getClass.getPackage.getName}"
    val lastpart = pname.split('.').last
    val name = s"test-$lastpart-reference.conf"
    val resource = ConfigFactory.parseResources(name)

    if (resource.isEmpty) {
      None
    } else {
      Some(AppConfig(resource.resolveWith(ConfigFactory.defaultOverrides())))
    }
  }

  protected def configOptions: ConfigInjectionOptions = ConfigInjectionOptions()
}
