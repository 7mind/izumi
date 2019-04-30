package com.github.pshirshov.izumi.distage.roles

import cats.effect.LiftIO
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ResolvedConfig}
import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisMember
import com.github.pshirshov.izumi.distage.model.definition.{AxisBase, EnvAxis}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.model.meta.{LibraryReference, RolesInfo}
import com.github.pshirshov.izumi.distage.roles.model.{AppActivation, DiAppBootstrapException, RoleService}
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.distage.roles.services.PluginSource.AllLoadedPlugins
import com.github.pshirshov.izumi.distage.roles.services.ResourceRewriter.RewriteRules
import com.github.pshirshov.izumi.distage.roles.services._
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema.ParserDef
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag


/**
  * Application flow:
  * 1. Parse commandline parameters
  * 2. Create "early logger" (console sink & configurable log level)
  * 3. Show startup banner
  * 4. Load raw config
  * 5. Create "late logger" using config
  * 6. Enumerate app plugins and bootstrap plugins
  * 7. Enumerate available roles, show role info and and apply merge strategy/conflict resolution
  * 8. Validate loaded roles (for non-emptyness and conflicts between bootstrap and app plugins)
  * 9. Build plan for DIEffect runner
  * 10. Build plan for integration checks
  * 11. Build plan for application
  * 12. Run roles
  * 13. Run services
  * 14. Await application termination
  * 15. Close autocloseables
  * 16. Shutdown executors
  */
abstract class RoleAppLauncher[F[_] : TagK : DIEffect] {

  import RoleAppLauncher._

  private val loggers = new EarlyLoggers()

  protected def bootstrapConfig: BootstrapConfig

  protected val hook: AppShutdownStrategy[F]

  protected def referenceLibraryInfo: Seq[LibraryReference] = Vector.empty

  final def launch(parameters: RawAppArgs): Unit = {
    val earlyLogger = loggers.makeEarlyLogger(parameters)
    showBanner(earlyLogger, referenceLibraryInfo)

    val plugins = makePluginLoader(bootstrapConfig).load()
    val roles = loadRoles(parameters, earlyLogger, plugins)

    val defBs = makeBootstrapMergeStrategy(earlyLogger, parameters).merge(plugins.bootstrap)
    // TODO: check that there are no conflicts

    earlyLogger.info(s"Loaded ${defBs.bindings.size -> "bootstrap bindings"}...")

    val config = makeConfigLoader(earlyLogger, parameters).buildConfig()
    val lateLogger = loggers.makeLateLogger(parameters, earlyLogger, config)

    val mergeStrategy = makeMergeStrategy(lateLogger, parameters, roles)
    val defApp = mergeStrategy.merge(plugins.app)
    lateLogger.info(s"Loaded ${defApp.bindings.size -> "app bindings"}...")

    validate(defBs, defApp)

    val roots = gcRoots(roles)

    val activation = new ActivationParser().parseActivation(earlyLogger, parameters, defApp, defaultActivations, requiredActivations)

    val options = contextOptions(parameters)
    val moduleProvider = makeModuleProvider(options, parameters, activation, roles, config, lateLogger)
    val bsModule = moduleProvider.bootstrapModules().merge overridenBy defBs

    val planner = makePlanner(options, bsModule, activation, lateLogger)
    val appModule = moduleProvider.appModules().merge overridenBy defApp
    val appPlan = planner.makePlan(roots, appModule)
    lateLogger.info(s"Planning finished. ${appPlan.app.keys.size -> "main ops"}, ${appPlan.integration.keys.size -> "integration ops"}, ${appPlan.runtime.keys.size -> "runtime ops"}")

    val r = makeExecutor(parameters, roles, lateLogger, appPlan.injector)
    r.runPlan(appPlan)
  }


  protected def defaultActivations: Map[AxisBase, AxisMember] = Map(EnvAxis -> EnvAxis.Production)

  protected def requiredActivations: Map[AxisBase, AxisMember] = Map.empty

  protected def gcRoots(rolesInfo: RolesInfo): Set[DIKey] = {
    rolesInfo.requiredComponents ++ Set(
      RuntimeDIUniverse.DIKey.get[ResolvedConfig],
      RuntimeDIUniverse.DIKey.get[Set[RoleService[F]]],
    )
  }


  protected def makeBootstrapMergeStrategy(lateLogger: IzLogger, parameters: RawAppArgs): PluginMergeStrategy = {
    Quirks.discard(lateLogger, parameters)
    SimplePluginMergeStrategy
  }

  protected def makeMergeStrategy(lateLogger: IzLogger, parameters: RawAppArgs, roles: RolesInfo): PluginMergeStrategy = {
    Quirks.discard(lateLogger, parameters, roles)
    SimplePluginMergeStrategy
  }

  protected def makePlanner(options: ContextOptions, bsModule: BootstrapModule, activation: AppActivation, lateLogger: IzLogger): RoleAppPlanner[F] = {
    new RoleAppPlannerImpl[F](options, bsModule, activation, lateLogger)
  }

  protected def makeExecutor(parameters: RawAppArgs, roles: RolesInfo, lateLogger: IzLogger, injector: Injector): RoleAppExecutor[F] = {
    new RoleAppExecutorImpl[F](hook, roles, injector, lateLogger, parameters)
  }


  protected def makeModuleProvider(options: ContextOptions, parameters: RawAppArgs, activation: AppActivation, roles: RolesInfo, config: AppConfig, lateLogger: IzLogger): ModuleProvider[F] = {
    new ModuleProviderImpl[F](
      lateLogger,
      config,
      roles,
      options,
      parameters,
      activation,
    )
  }

  protected def contextOptions(parameters: RawAppArgs): ContextOptions = {
    val dumpContext = RoleAppLauncher.Options.dumpContext.hasFlag(parameters.globalParameters)
    ContextOptions(
      dumpContext,
      warnOnCircularDeps = true,
      RewriteRules(),
      ConfigInjectionOptions(),
    )
  }

  protected def loadRoles(parameters: RawAppArgs, logger: IzLogger, plugins: AllLoadedPlugins): RolesInfo = {
    val activeRoleNames = parameters.roles.map(_.role).toSet
    val mp = MirrorProvider.Impl
    val roleProvider: RoleProvider[F] = new RoleProviderImpl(logger, activeRoleNames, mp)
    val bindings = plugins.app.flatMap(_.bindings)
    val bsBindings = plugins.app.flatMap(_.bindings)
    logger.info(s"Available ${plugins.app.size -> "app plugins"} with ${bindings.size -> "app bindings"} and ${plugins.bootstrap.size -> "bootstrap plugins"} with ${bsBindings.size -> "bootstrap bindings"} ...")
    val roles = roleProvider.getInfo(bindings)

    printRoleInfo(logger, roles)
    val missing = parameters.roles.map(_.role).toSet.diff(roles.availableRoleBindings.map(_.descriptor.id).toSet)
    if (missing.nonEmpty) {
      logger.crit(s"Missing ${missing.niceList() -> "roles"}")
      throw new DiAppBootstrapException(s"Unknown roles: $missing")
    }

    roles
  }

  protected def printRoleInfo(logger: IzLogger, roles: RolesInfo): Unit = {
    val requestedNames = roles.requiredRoleBindings.map(_.descriptor.id)

    val availableRoleInfo = roles.availableRoleBindings.map {
      r =>
        val active = if (requestedNames.contains(r.descriptor.id)) {
          s"[+]"
        } else {
          s"[ ]"
        }
        s"$active ${r.descriptor.id}, ${r.tpe}, source=${r.source.getOrElse("N/A")}"
    }.sorted

    logger.info(s"Available ${availableRoleInfo.niceList() -> "roles"}")
  }


  protected def showBanner(logger: IzLogger, referenceLibraries: Seq[LibraryReference]): this.type = {
    val withIzumi = referenceLibraries :+ LibraryReference("izumi-r2", classOf[ConfigLoader])
    showDepData(logger, "Application is about to start", this.getClass)
    withIzumi.foreach { u => showDepData(logger, s"... using ${u.libraryName}", u.clazz) }
    this
  }

  private def showDepData(logger: IzLogger, msg: String, clazz: Class[_]): Unit = {
    val mf = IzManifest.manifest()(ClassTag(clazz)).map(IzManifest.read)
    val details = mf.getOrElse("{No version data}")
    logger.info(s"$msg : $details")
  }

  protected def validate(bootstrapAutoDef: ModuleBase, appDef: ModuleBase): Unit = {
    val conflicts = bootstrapAutoDef.keys.intersect(appDef.keys)
    if (conflicts.nonEmpty) {
      throw new DiAppBootstrapException(s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating...")
    }

    if (appDef.bindings.isEmpty) {
      throw new DiAppBootstrapException("Empty app object graph. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
    }
  }

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    new PluginSourceImpl(bootstrapConfig)
  }

  protected def makeConfigLoader(logger: IzLogger, parameters: RawAppArgs): ConfigLoader = {
    val maybeGlobalConfig = Options.configParam.findValue(parameters.globalParameters).asFile

    val roleConfigs = parameters.roles.map {
      r =>
        r.role -> Options.configParam.findValue(r.roleParameters).asFile
    }
    new ConfigLoaderLocalFSImpl(logger, maybeGlobalConfig, roleConfigs.toMap)
  }

}

object RoleAppLauncher {

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{hocon|json}")
    final val configParam = arg("config", "c", "path to config file", "<path>")
    final val dumpContext = flag("debug-dump-graph", "dump DI graph for debugging")
    final val use = arg("use", "u", "activate choice on funcionality axis", "<axis>:<choice>")
  }

  abstract class LauncherF[F[_] : TagK : DIEffect : LiftIO](executionContext: ExecutionContext = global) extends RoleAppLauncher[F] {
    override protected val hook: AppShutdownStrategy[F] = new CatsEffectIOShutdownStrategy(executionContext)
  }

  abstract class LauncherIdentity extends RoleAppLauncher[Identity] {
    override protected val hook: AppShutdownStrategy[Identity] = new JvmExitHookLatchShutdownStrategy
  }

}


