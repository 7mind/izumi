package izumi.distage.roles.launcher

import java.io.File

import distage._
import izumi.distage.config.codec.DIConfigReader
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services._
import izumi.distage.model.definition.Activation
import izumi.distage.model.recursive.Bootloader
import izumi.distage.plugins.load.{PluginLoader, PluginLoaderDefaultImpl}
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.plugins.{PluginBase, PluginConfig}
import izumi.distage.roles.launcher.RoleAppLauncherImpl.{ActivationConfig, Options}
import izumi.distage.roles.model.exceptions.DIAppBootstrapException
import izumi.distage.roles.model.meta.{LibraryReference, RolesInfo}
import izumi.distage.roles.launcher.services.StartupPlanExecutor.{Filters, PreparedApp}
import izumi.distage.roles.launcher.services.{RoleAppActivationParser, _}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.cli.model.schema.ParserDef
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.platform.resources.IzManifest
import izumi.fundamentals.platform.strings.IzString._
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.{IzLogger, Log}

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
// FIXME: rewrite using DI https://github.com/7mind/izumi/issues/779
case class RoleAppLauncherImpl[F[_]: TagK](
  protected val shutdownStrategy: AppShutdownStrategy[F],
  protected val pluginConfig: PluginConfig,
) extends RoleAppLauncher[F] {

  protected def bootstrapPluginConfig: PluginConfig = PluginConfig.empty
  //protected def shutdownStrategy: AppShutdownStrategy[F]

  protected def additionalLibraryReferences: Seq[LibraryReference] = Vector.empty

  protected def appOverride: ModuleBase = ModuleBase.empty
  protected def bsOverride: BootstrapModule = BootstrapModule.empty

  protected def defaultActivations: Activation = StandardAxis.prodActivation
  protected def requiredActivations: Activation = Activation.empty

  protected def defaultLogLevel: Log.Level = Log.Level.Info
  protected def defaultLogFormatJson: Boolean = false

  protected def configActivationSection: String = "activation"
  protected def defaultBaseConfigs: Seq[String] = ConfigLoader.defaultBaseConfigs

  def launch(parameters: RawAppArgs): DIResourceBase[Identity, PreparedApp[F]] = {
    val earlyLogger = EarlyLoggers.makeEarlyLogger(parameters, defaultLogLevel)
    showBanner(earlyLogger, additionalLibraryReferences)

    val bsPlugins = makeBootstrapPluginLoader(earlyLogger).load(bootstrapPluginConfig)
    val appPlugins = makePluginLoader(earlyLogger).load(pluginConfig)
    val roles = loadRoles(parameters, earlyLogger, appPlugins, bsPlugins)

    val config = makeConfigLoader(earlyLogger, parameters).loadConfig()
    val lateLogger = EarlyLoggers.makeLateLogger(parameters, earlyLogger, config, defaultLogLevel, defaultLogFormatJson)

    val appPlan = createAppPlan(parameters, roles, appPlugins, bsPlugins, config, lateLogger)
    lateLogger.info(s"Planning finished. ${appPlan.app.primary.keys.size -> "main ops"}, ${appPlan.app.side.keys.size -> "integration ops"}, ${appPlan
      .app.shared.keys.size -> "shared ops"}, ${appPlan.runtime.keys.size -> "runtime ops"}")

    val roleAppExecutor = {
      val injector = appPlan.injector
      val startupExecutor = makeStartupExecutor(lateLogger, injector)
      makeExecutor(parameters, roles, lateLogger, startupExecutor)
    }
    roleAppExecutor.runPlan(appPlan)
  }

  def createAppPlan(
    parameters: RawAppArgs,
    roles: RolesInfo,
    appPlugins: Seq[PluginBase],
    bsPlugins: Seq[PluginBase],
    config: AppConfig,
    lateLogger: IzLogger,
  ): RoleAppPlanner.AppStartupPlans = {
    val bsModule = makeBootstrapMergeStrategy(lateLogger, parameters, roles).merge(bsPlugins)
    lateLogger.info(s"Loaded ${bsModule.bindings.size -> "bootstrap bindings"}...")

    val appModule = makeAppMergeStrategy(lateLogger, parameters, roles).merge(appPlugins)
    lateLogger.info(s"Loaded ${appModule.bindings.size -> "app bindings"}...")

    validate(bsModule, appModule)

    val activationInfo = parseActivationInfo(lateLogger, appModule)
    val activation = {
      defaultActivations ++
      requiredActivations ++
      parseActivation(lateLogger, parameters, roles, config, activationInfo)
    }

    val options = planningOptions(parameters)
    val moduleProvider = makeModuleProvider(options, parameters, activationInfo, activation, roles, config, lateLogger.router)

    val finalBsModule = moduleProvider.bootstrapModules().merge overridenBy bsModule overridenBy bsOverride
    val finalAppModule = moduleProvider.appModules().merge overridenBy appModule overridenBy appOverride
    val roots = gcRoots(roles)
    val bootloader = Injector.bootloader(PlannerInput(finalAppModule, activation, roots), activation)
    val planner = makePlanner(options, finalBsModule, lateLogger, bootloader)
    planner.makePlan(roots)
  }

  protected def gcRoots(rolesInfo: RolesInfo): Set[DIKey] = rolesInfo.requiredComponents
  protected def makeBootstrapMergeStrategy(@unused lateLogger: IzLogger, @unused parameters: RawAppArgs, @unused roles: RolesInfo): PluginMergeStrategy =
    SimplePluginMergeStrategy
  protected def makeAppMergeStrategy(@unused lateLogger: IzLogger, @unused parameters: RawAppArgs, @unused roles: RolesInfo): PluginMergeStrategy =
    SimplePluginMergeStrategy

  protected def makePluginLoader(@unused earlyLogger: IzLogger): PluginLoader = new PluginLoaderDefaultImpl()
  protected def makeBootstrapPluginLoader(@unused earlyLogger: IzLogger): PluginLoader = new PluginLoaderDefaultImpl()

  protected def makePlanner(options: PlanningOptions, bsModule: BootstrapModule, lateLogger: IzLogger, reboot: Bootloader): RoleAppPlanner[F] = {
    new RoleAppPlanner.Impl[F](options, bsModule, lateLogger, reboot)
  }

  protected def makeExecutor(
    parameters: RawAppArgs,
    roles: RolesInfo,
    lateLogger: IzLogger,
    startupPlanExecutor: StartupPlanExecutor[F],
    filters: Filters[F] = Filters.all,
  ): RoleAppExecutor[F] = {
    new RoleAppExecutor.Impl[F](shutdownStrategy, roles, lateLogger, parameters, startupPlanExecutor, filters)
  }

  protected def makeStartupExecutor(lateLogger: IzLogger, injector: Injector): StartupPlanExecutor[F] = {
    StartupPlanExecutor(injector, new IntegrationChecker.Impl[F](lateLogger))
  }

  protected def makeModuleProvider(
    options: PlanningOptions,
    parameters: RawAppArgs,
    activationInfo: ActivationInfo,
    @unused activation: Activation,
    roles: RolesInfo,
    config: AppConfig,
    logRouter: LogRouter,
  ): ModuleProvider = {
    new ModuleProvider.Impl(
      logRouter = logRouter,
      config = config,
      roles = roles,
      options = options,
      args = parameters,
      activationInfo = activationInfo,
    )
  }

  protected def planningOptions(parameters: RawAppArgs): PlanningOptions = {
    PlanningOptions(
      addGraphVizDump = parameters.globalParameters.hasFlag(Options.dumpContext)
    )
  }

  protected def loadRoles(parameters: RawAppArgs, logger: IzLogger, appPlugins: Seq[PluginBase], bsPlugins: Seq[PluginBase]): RolesInfo = {
    val bindings = appPlugins.flatMap(_.bindings)
    val bsBindings = bsPlugins.flatMap(_.bindings)
    logger.info(
      s"Available ${appPlugins.size -> "app plugins"} with ${bindings.size -> "app bindings"} and ${bsPlugins.size -> "bootstrap plugins"} with ${bsBindings.size -> "bootstrap bindings"} ..."
    )

    val activeRoleNames = parameters.roles.map(_.role).toSet
    val roleProvider = makeRoleProvider(logger, activeRoleNames)
    val roles = roleProvider.getInfo(bindings)

    logger.info(s"Available ${roles.render() -> "roles"}")

    val missing = parameters.roles.map(_.role).toSet.diff(roles.availableRoleBindings.map(_.descriptor.id).toSet)
    if (missing.nonEmpty) {
      logger.crit(s"Missing ${missing.niceList() -> "roles"}")
      throw new DIAppBootstrapException(s"Unknown roles: $missing")
    }
    if (roles.requiredRoleBindings.isEmpty) {
      throw new DIAppBootstrapException(s"""No roles selected to launch, please select one of the following roles using syntax `:${'$'}roleName` on the command-line.
                                           |
                                           |Available roles: ${roles.render()}""".stripMargin)
    }

    roles
  }

  protected def makeRoleProvider(logger: IzLogger, activeRoleNames: Set[String]): RoleProvider[F] = {
    new RoleProvider.Impl(logger, activeRoleNames, reflectionEnabled = true)
  }

  protected def showBanner(logger: IzLogger, referenceLibraries: Seq[LibraryReference]): Unit = {
    def showDepData(logger: IzLogger, msg: String, clazz: Class[_]): Unit = {
      val mf = IzManifest.manifest()(ClassTag(clazz)).map(IzManifest.read)
      val details = mf.getOrElse("{No version data}")
      logger.info(s"$msg : $details")
    }

    val withIzumi = referenceLibraries :+ LibraryReference("izumi", classOf[ConfigLoader])
    showDepData(logger, "Application is about to start", this.getClass)
    withIzumi.foreach {
      lib => showDepData(logger, s"... using ${lib.libraryName}", lib.clazz)
    }
  }

  protected def validate(bootstrapAutoModule: ModuleBase, appModule: ModuleBase): Unit = {
    val conflicts = bootstrapAutoModule.keys.intersect(appModule.keys)
    if (conflicts.nonEmpty)
      throw new DIAppBootstrapException(
        s"Same keys defined by bootstrap and app plugins: $conflicts. Most likely your bootstrap configs are contradictive, terminating..."
      )
    if (appModule.bindings.isEmpty)
      throw new DIAppBootstrapException("Empty app object graph. Most likely you have no plugins defined or your app plugin config is wrong, terminating...")
  }

  protected def makeConfigLoader(earlyLogger: IzLogger, parameters: RawAppArgs): ConfigLoader = {
    val (maybeGlobalConfig, roleConfigs) = makeConfigLoaderParameters(parameters)
    new ConfigLoader.LocalFSImpl(earlyLogger, maybeGlobalConfig, roleConfigs, defaultBaseConfigs)
  }

  protected def makeConfigLoaderParameters(parameters: RawAppArgs): (Option[File], Map[String, Option[File]]) = {
    val maybeGlobalConfig = parameters.globalParameters.findValue(Options.configParam).asFile
    val roleConfigs = parameters.roles.map {
      roleParams =>
        roleParams.role -> roleParams.roleParameters.findValue(Options.configParam).asFile
    }
    (maybeGlobalConfig, roleConfigs.toMap)
  }

  protected def parseActivationInfo(@unused lateLogger: IzLogger, appModule: ModuleBase): ActivationInfo = {
    ActivationInfoExtractor.findAvailableChoices(appModule)
  }

  /** Note, besides overriding this method, activation parsing strategy can also be changed by using bootstrap modules or plugins
    * and adding a binding for `make[Activation]`
    */
  protected def parseActivation(
    lateLogger: IzLogger,
    parameters: RawAppArgs,
    @unused rolesInfo: RolesInfo,
    config: AppConfig,
    activationInfo: ActivationInfo,
  ): Activation = {
    val parser = new RoleAppActivationParser.Impl(lateLogger)

    val cmdChoices = parameters.globalParameters.findValues(Options.use).map(_.value.split2(':'))
    val cmdActivations = parser.parseActivation(cmdChoices, activationInfo)

    val configChoices = if (config.config.hasPath(configActivationSection)) {
      ActivationConfig.diConfigReader.decodeConfig(configActivationSection)(config.config).choices
    } else Map.empty
    val configActivations = parser.parseActivation(configChoices, activationInfo)

    configActivations ++ cmdActivations // commandline choices override values in config
  }

}

object RoleAppLauncherImpl {

  final case class ActivationConfig(choices: Map[String, String])

  object ActivationConfig {
    implicit val diConfigReader: DIConfigReader[ActivationConfig] = DIConfigReader[Map[String, String]].map(ActivationConfig(_))
  }

  object Options extends ParserDef {
    final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
    final val logFormatParam = arg("log-format", "lf", "log format", "{hocon|json}")
    final val configParam = arg("config", "c", "path to config file", "<path>")
    final val dumpContext = flag("debug-dump-graph", "dump DI graph for debugging")
    final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  }
}
