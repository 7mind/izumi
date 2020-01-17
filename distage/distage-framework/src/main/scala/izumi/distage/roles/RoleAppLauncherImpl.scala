package izumi.distage.roles

import distage._
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ResourceRewriter.RewriteRules
import izumi.distage.framework.services.{ConfigLoader, IntegrationChecker, ModuleProvider, RoleAppPlanner}
import izumi.distage.model.definition.Activation
import izumi.distage.model.effect.DIEffect
import izumi.distage.plugins.PluginBase
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.RoleAppLauncher.Options
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.distage.roles.model.meta.{LibraryReference, RolesInfo}
import izumi.distage.roles.services.StartupPlanExecutor.Filters
import izumi.distage.roles.services.{RoleAppActivationParser, _}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.resources.IzManifest
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.IzLogger

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
  * 12. Run role tasks
  * 13. Run role services
  * 14. Await application termination
  * 15. Run finalizers
  * 16. Shutdown executors
  */
abstract class RoleAppLauncherImpl[F[_]: TagK: DIEffect] extends RoleAppLauncher {
  protected def pluginLoader: PluginLoader
  protected def bootstrapPluginLoader: PluginLoader = PluginLoader.empty
  protected def shutdownStrategy: AppShutdownStrategy[F]

  protected def referenceLibraryInfo: Seq[LibraryReference] = Vector.empty

  protected def appOverride: ModuleBase = ModuleBase.empty
  protected def bsOverride: BootstrapModule = BootstrapModule.empty

  protected def defaultActivations: Activation = StandardAxis.prodActivation
  protected def requiredActivations: Activation = Activation.empty

  def launch(parameters: RawAppArgs): Unit = {
    val earlyLogger = EarlyLoggers.makeEarlyLogger(parameters)
    showBanner(earlyLogger, referenceLibraryInfo)

    val appPlugins = pluginLoader.load()
    val bsPlugins = bootstrapPluginLoader.load()
    val roles = loadRoles(parameters, earlyLogger, appPlugins, bsPlugins)

    // default PlanMergingPolicy will be applied to bootstrap module, so any non-trivial conflict in bootstrap bindings will fail the app
    val bsDefinition = makeBootstrapMergeStrategy(earlyLogger, parameters).merge(bsPlugins)

    earlyLogger.info(s"Loaded ${bsDefinition.bindings.size -> "bootstrap bindings"}...")

    val config = makeConfigLoader(earlyLogger, parameters).buildConfig()
    val lateLogger = EarlyLoggers.makeLateLogger(parameters, earlyLogger, config)

    val mergeStrategy = makeMergeStrategy(lateLogger, parameters, roles)
    val appDefinition = mergeStrategy.merge(appPlugins)
    lateLogger.info(s"Loaded ${appDefinition.bindings.size -> "app bindings"}...")

    validate(bsDefinition, appDefinition)

    val roots = gcRoots(roles)

    val (activationInfo, activation) = new RoleAppActivationParser().parseActivation(earlyLogger, parameters, appDefinition, defaultActivations ++ requiredActivations)

    val options = contextOptions(parameters)
    val moduleProvider = makeModuleProvider(options, parameters, activationInfo, activation, roles, config, lateLogger)
    val bsModule = moduleProvider.bootstrapModules().merge overridenBy bsDefinition overridenBy bsOverride

    val appModule = moduleProvider.appModules().merge overridenBy appDefinition overridenBy appOverride
    val planner = makePlanner(options, bsModule, lateLogger)
    val appPlan = planner.makePlan(roots, appModule)
    lateLogger.info(s"Planning finished. ${appPlan.app.primary.keys.size -> "main ops"}, ${appPlan.app.side.keys.size -> "integration ops"}, ${appPlan.app.shared.keys.size -> "shared ops"}, ${appPlan.runtime.keys.size -> "runtime ops"}")

    val injector = appPlan.injector
    val roleAppExecutor = makeExecutor(parameters, roles, lateLogger, makeStartupExecutor(lateLogger, injector))
    roleAppExecutor.runPlan(appPlan)
  }

  protected def gcRoots(rolesInfo: RolesInfo): Set[DIKey] = rolesInfo.requiredComponents
  protected def makeBootstrapMergeStrategy(@unused lateLogger: IzLogger, @unused parameters: RawAppArgs): PluginMergeStrategy = SimplePluginMergeStrategy
  protected def makeMergeStrategy(@unused lateLogger: IzLogger, @unused parameters: RawAppArgs, @unused roles: RolesInfo): PluginMergeStrategy = SimplePluginMergeStrategy

  protected def makePlanner(options: PlanningOptions, bsModule: BootstrapModule, lateLogger: IzLogger): RoleAppPlanner[F] = {
    new RoleAppPlanner.Impl[F](options, bsModule, lateLogger)
  }

  protected def makeExecutor(parameters: RawAppArgs, roles: RolesInfo, lateLogger: IzLogger, startupPlanExecutor: StartupPlanExecutor[F], filters: Filters[F] = Filters.all): RoleAppExecutor[F] = {
    new RoleAppExecutor.Impl[F](shutdownStrategy, roles, lateLogger, parameters, startupPlanExecutor, filters)
  }

  protected def makeStartupExecutor(lateLogger: IzLogger, injector: Injector): StartupPlanExecutor[F] = {
    StartupPlanExecutor(injector, new IntegrationChecker.Impl[F](lateLogger))
  }

  protected def makeModuleProvider(options: PlanningOptions, parameters: RawAppArgs, activationInfo: ActivationInfo, activation: Activation, roles: RolesInfo, config: AppConfig, lateLogger: IzLogger): ModuleProvider = {
    new ModuleProvider.Impl[F](
      logger = lateLogger,
      config = config,
      roles = roles,
      options = options,
      args = parameters,
      activationInfo = activationInfo,
      activation = activation,
    )
  }

  protected def contextOptions(parameters: RawAppArgs): PlanningOptions = {
    PlanningOptions(
      addGraphVizDump = parameters.globalParameters.hasFlag(Options.dumpContext),
      warnOnCircularDeps = true,
      rewriteRules = RewriteRules(),
    )
  }

  protected def loadRoles(parameters: RawAppArgs, logger: IzLogger, appPlugins: Seq[PluginBase], bsPlugins: Seq[PluginBase]): RolesInfo = {
    val bindings = appPlugins.flatMap(_.bindings)
    val bsBindings = bsPlugins.flatMap(_.bindings)
    logger.info(s"Available ${appPlugins.size -> "app plugins"} with ${bindings.size -> "app bindings"} and ${
      bsPlugins.size -> "bootstrap plugins"} with ${bsBindings.size -> "bootstrap bindings"} ...")

    val activeRoleNames = parameters.roles.map(_.role).toSet
    val roleProvider = new RoleProvider.Impl(logger, activeRoleNames)
    val roles = roleProvider.getInfo(bindings)
    printRoleInfo(logger, roles)

    val missing = parameters.roles.map(_.role).toSet.diff(roles.availableRoleBindings.map(_.descriptor.id).toSet)
    if (missing.nonEmpty) {
      logger.crit(s"Missing ${missing.niceList() -> "roles"}")
      throw new DIAppBootstrapException(s"Unknown roles: $missing")
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
        s"$active ${r.descriptor.id}, ${r.binding.key}, source=${r.source.getOrElse("N/A")}"
    }.sorted

    logger.info(s"Available ${availableRoleInfo.niceList() -> "roles"}")
  }

  protected def showBanner(logger: IzLogger, referenceLibraries: Seq[LibraryReference]): this.type = {
    val withIzumi = referenceLibraries :+ LibraryReference("izumi", classOf[ConfigLoader])
    showDepData(logger, "Application is about to start", this.getClass)
    withIzumi.foreach {
      u => showDepData(logger, s"... using ${u.libraryName}", u.clazz)
    }
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
      throw new DIAppBootstrapException(s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating...")
    }

    if (appDef.bindings.isEmpty) {
      throw new DIAppBootstrapException("Empty app object graph. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
    }
  }

  protected def makeConfigLoader(logger: IzLogger, parameters: RawAppArgs): ConfigLoader = {
    val maybeGlobalConfig = parameters.globalParameters.findValue(Options.configParam).asFile

    val roleConfigs = parameters.roles.map {
      roleParams =>
        roleParams.role -> roleParams.roleParameters.findValue(Options.configParam).asFile
    }
    new ConfigLoader.LocalFSImpl(logger, maybeGlobalConfig, roleConfigs.toMap)
  }

}
