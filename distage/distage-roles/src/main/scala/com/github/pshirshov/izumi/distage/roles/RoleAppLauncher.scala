package com.github.pshirshov.izumi.distage.roles

import java.io.File

import cats.effect.LiftIO
import com.github.pshirshov.izumi.distage.app.{BootstrapConfig, DiAppBootstrapException}
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import com.github.pshirshov.izumi.distage.plugins.MergedPlugins
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.services.PluginSource.AllLoadedPlugins
import com.github.pshirshov.izumi.distage.roles.services._
import com.github.pshirshov.izumi.fundamentals.platform.cli.{CLIParser, RoleAppArguments}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzManifest
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import CLIParser._
import com.github.pshirshov.izumi.distage.config.ResolvedConfig
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks


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

  protected def referenceLibraryInfo: Seq[Using] = Vector.empty

  final def launch(parameters: RoleAppArguments): Unit = {
    val earlyLogger = loggers.makeEarlyLogger(parameters)
    showBanner(earlyLogger, referenceLibraryInfo)

    val plugins = makePluginLoader(bootstrapConfig).load()

    val roles = loadRoles(parameters, earlyLogger, plugins)

    val mergeStrategy = makeMergeProvider(earlyLogger, parameters).mergeStrategy(plugins, roles)
    val defBs = mergeStrategy.merge(plugins.bootstrap)
    val defApp = mergeStrategy.merge(plugins.app)
    validate(defBs, defApp)
    earlyLogger.info(s"Loaded ${defApp.definition.bindings.size -> "app bindings"} and ${defBs.definition.bindings.size -> "bootstrap bindings"}...")

    val config = makeConfigLoader(earlyLogger, parameters).buildConfig()
    val lateLogger = loggers.makeLateLogger(parameters, earlyLogger, config)
    val moduleProvider = makeModuleProvider(parameters, config, lateLogger, roles)
    val bsModule = moduleProvider.bootstrapModules().merge overridenBy defBs.definition
    val appModule = moduleProvider.appModules().merge overridenBy defApp.definition

    val injector = Injector.Standard(bsModule)
    val p = makePlanner(appModule, roles, injector)
    val roots = gcRoots(roles, defBs, defApp)
    val appPlan = p.makePlan(roots)
    lateLogger.info(s"Planning finished. ${appPlan.app.keys.size -> "main ops"}, ${appPlan.integration.keys.size -> "integration ops"}, ${appPlan.runtime.keys.size -> "runtime ops"}")

    val r = makeExecutor(parameters, roles, lateLogger, injector)
    r.runPlan(appPlan)
  }

  protected def gcRoots(rolesInfo: RolesInfo, bs: MergedPlugins, app: MergedPlugins): Set[DIKey] = {
    Quirks.discard(bs, app)
    rolesInfo.requiredComponents ++ Set(
      RuntimeDIUniverse.DIKey.get[ResolvedConfig],
      RuntimeDIUniverse.DIKey.get[Set[RoleService2[F]]],
      RuntimeDIUniverse.DIKey.get[Finalizers.CloseablesFinalized],
    )
  }

  protected def makePlanner(module: distage.Module, roles: RolesInfo, injector: Injector): RoleAppPlanner[F] = {
    new RoleAppPlannerImpl[F](module, roles, injector)
  }

  protected def makeExecutor(parameters: RoleAppArguments, roles: RolesInfo, lateLogger: IzLogger, injector: Injector): RoleAppExecutor[F] = {
    new RoleAppExecutorImpl[F](hook, roles, injector, lateLogger, parameters)
  }

  protected val hook: ApplicationShutdownStrategy[F]

  protected def makeModuleProvider(parameters: RoleAppArguments, config: AppConfig, lateLogger: IzLogger, roles: RolesInfo): ModuleProvider[F] = {
    new ModuleProviderImpl[F](lateLogger, parameters, config, roles)
  }

  protected def loadRoles(parameters: RoleAppArguments, logger: IzLogger, plugins: AllLoadedPlugins): RolesInfo = {
    val activeRoleNames = parameters.roles.map(_.role).toSet
    val mp = MirrorProvider.Impl
    val roleProvider: RoleProvider[F] = new RoleProviderImpl(logger, activeRoleNames, mp)
    val bindings = plugins.app.flatMap(_.bindings)
    val bsBindings = plugins.app.flatMap(_.bindings)
    logger.info(s"Available ${plugins.app.size -> "app plugins"} with ${bindings.size -> "app bindings"} and ${plugins.bootstrap.size -> "bootstrap plugins"} with ${bsBindings.size -> "bootstrap bindings"} ...")
    val roles = roleProvider.getInfo(bindings)

    printRoleInfo(logger, roles)
    val missing = parameters.roles.map(_.role).toSet.diff(roles.availableRoleBindings.map(_.name).toSet)
    if (missing.nonEmpty) {
      logger.crit(s"Missing ${missing.niceList() -> "roles"}")
      throw new DiAppBootstrapException(s"Unknown roles: $missing")
    }

    roles
  }

  protected def printRoleInfo(logger: IzLogger, roles: RolesInfo): Unit = {
    val requestedNames = roles.requiredRoleBindings.map(_.name)

    val availableRoleInfo = roles.availableRoleBindings.map {
      r =>
        val active = if (requestedNames.contains(r.name)) {
          s"[+]"
        } else {
          s"[ ]"
        }
        s"$active ${r.name}, ${r.tpe}, source=${r.source.getOrElse("N/A")}"
    }.sorted

    logger.info(s"Available ${availableRoleInfo.niceList() -> "roles"}")
  }


  protected def showBanner(logger: IzLogger, referenceLibraries: Seq[Using]): this.type = {
    val withIzumi = referenceLibraries :+ Using("izumi-r2", classOf[ConfigLoader])
    showDepData(logger, "Application is about to start", this.getClass)
    withIzumi.foreach { u => showDepData(logger, s"... using ${u.libraryName}", u.clazz) }
    this
  }

  private def showDepData(logger: IzLogger, msg: String, clazz: Class[_]): Unit = {
    val mf = IzManifest.manifest()(ClassTag(clazz)).map(IzManifest.read)
    val details = mf.getOrElse("{No version data}")
    logger.info(s"$msg : $details")
  }

  protected def validate(bootstrapAutoDef: MergedPlugins, appDef: MergedPlugins): Unit = {
    val conflicts = bootstrapAutoDef.definition.keys.intersect(appDef.definition.keys)
    if (conflicts.nonEmpty) {
      throw new DiAppBootstrapException(s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating...")
    }

    if (appDef.definition.bindings.isEmpty) {
      throw new DiAppBootstrapException("Empty app object graph. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
    }
  }

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    new PluginSourceImpl(bootstrapConfig)
  }

  protected def makeConfigLoader(logger: IzLogger, parameters: RoleAppArguments): ConfigLoader = {
    val maybeGlobalConfig = configParam.findValue(parameters.globalParameters).asFile

    val roleConfigs = parameters.roles.map {
      r =>
        r.role -> configParam.findValue(r.roleParameters).map(v => new File(v.value))
    }
    new ConfigLoaderLocalFilesystemImpl(logger, maybeGlobalConfig, roleConfigs.toMap)
  }

  protected def makeMergeProvider(lateLogger: IzLogger, parameters: RoleAppArguments): MergeProvider = {
    new MergeProviderImpl(lateLogger, parameters)
  }
}

object RoleAppLauncher {
  final val logLevelRootParam = ArgDef(ArgNameDef("log-level-root", "ll"))
  final val logFormatParam = ArgDef(ArgNameDef("log-format", "logs"))
  final val configParam = ArgDef(ArgNameDef("config", "c"))
  final val dumpContext = ArgDef(ArgNameDef("debug-dump-graph"))

  @deprecated("We should stop using tags", "2019-04-19")
  final val useDummies = CLIParser.ArgNameDef("mode:dummies")

  abstract class LauncherF[F[_] : TagK : DIEffect : LiftIO](executionContext: ExecutionContext = global) extends RoleAppLauncher[F] {
    override protected val hook: ApplicationShutdownStrategy[F] = new CatsEffectIOShutdownStrategy(executionContext)
  }

  abstract class LauncherIdentity extends RoleAppLauncher[Identity] {
    override protected val hook: ApplicationShutdownStrategy[Identity] = new JvmExitHookLatchShutdownStrategy
  }

}
