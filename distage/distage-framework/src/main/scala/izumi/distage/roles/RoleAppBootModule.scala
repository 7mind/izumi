package izumi.distage.roles

import izumi.distage.InjectorFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.*
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.*
import izumi.distage.model.recursive.{Bootloader, LocatorRef}
import izumi.distage.model.reflection.DIKey
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderDefaultImpl}
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.RoleAppMain.{ArgV, RequiredRoles}
import izumi.distage.roles.launcher.*
import izumi.distage.roles.launcher.AppResourceProvider.{AppResource, FinalizerFilters}
import izumi.distage.roles.launcher.LateLoggerFactory.DistageAppLogging
import izumi.distage.roles.launcher.ModuleValidator.ValidatedModulePair
import izumi.distage.roles.model.meta.{LibraryReference, RolesInfo}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.cli.{CLIParser, CLIParserImpl, ParserFailureHandler}
import izumi.fundamentals.platform.resources.IzArtifact
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.{IzLogger, Log}
import izumi.reflect.TagK

/**
  * This module is only used by the application launcher, but NOT by distage-testkit
  *
  * Application flow:
  * 1. Parse commandline parameters
  * 2. Create "early logger" (console sink & configurable log level)
  * 3. Show startup banner
  * 4. Load raw config
  * 5. Create "late logger" using config
  * 6. Enumerate app plugins and bootstrap plugins
  * 7. Enumerate available roles, show role info and and apply merge strategy/conflict resolution
  * 8. Validate loaded roles (for non-emptyness and conflicts between bootstrap and app plugins)
  * 9. Build plan for [[izumi.functional.quasi.QuasiIORunner]]
  * 10. Build plan for integration checks
  * 11. Build plan for application
  * 12. Run role tasks
  * 13. Run role services
  * 14. Await application termination
  * 15. Run finalizers
  * 16. Shutdown executors
  */
class RoleAppBootModule[F[_]: TagK: DefaultModule](
  args: ArgV,
  requiredRoles: RequiredRoles,
  shutdownStrategy: AppShutdownStrategy[F],
  pluginConfig: PluginConfig,
  bootstrapPluginConfig: PluginConfig,
  appArtifact: IzArtifact,
  unusedValidAxisChoices: Set[Axis.AxisChoice],
) extends ModuleDef {
  addImplicit[TagK[F]]
  addImplicit[DefaultModule[F]]

  make[ArgV].fromValue(args)
  make[RequiredRoles].fromValue(requiredRoles)

  make[AppShutdownInitiator].using[AppShutdownStrategy[F]]
  make[AppShutdownStrategy[F]].fromValue(shutdownStrategy)
  make[PluginConfig].named("main").fromValue(pluginConfig)
  make[PluginConfig].named("bootstrap").fromValue(bootstrapPluginConfig)

  make[Option[IzArtifact]].named("app.artifact").fromValue(Some(appArtifact))

  make[CLIParser].from[CLIParserImpl]
  make[ParserFailureHandler].from(ParserFailureHandler.TerminatingHandler)
  make[AppArgsInterceptor].from[AppArgsInterceptor.Impl]

  make[RawAppArgs].from {
    (parser: CLIParser, args: ArgV, handler: ParserFailureHandler, interceptor: AppArgsInterceptor, additionalRoles: RequiredRoles) =>
      parser.parse(args.args) match {
        case Left(error) =>
          handler.onParserError(error)
        case Right(args) =>
          interceptor.rolesToLaunch(args, additionalRoles)
      }
  }

  many[LibraryReference]

  make[CLILoggerOptionsReader].from[CLILoggerOptionsReader.CLILoggerOptionsReaderImpl]
  make[CLILoggerOptions].from {
    (reader: CLILoggerOptionsReader) =>
      reader.read()
  }
  make[EarlyLoggerFactory].from[EarlyLoggerFactory.EarlyLoggerFactoryImpl]
  make[LateLoggerFactory].from[LateLoggerFactory.LateLoggerFactoryImpl]

  make[Log.Level].named("early").fromValue(Log.Level.Info)
  make[IzLogger].named("early").from {
    (factory: EarlyLoggerFactory, banner: StartupBanner) =>
      val logger = factory.makeEarlyLogger()
      banner.showBanner(logger)
      logger
  }
  make[DistageAppLogging].fromResource {
    (factory: LateLoggerFactory) =>
      factory.makeLateLogRouter()
  }
  make[LogRouter].from {
    (logging: DistageAppLogging) =>
      logging.router
  }

  make[IzLogger].from {
    (router: LogRouter) =>
      IzLogger(router)("phase" -> "late")
  }

  make[StartupBanner].from[StartupBanner.Impl]

  make[PluginLoader]
    .named("bootstrap")
    .aliased[PluginLoader]("main")
    .from[PluginLoaderDefaultImpl]

  make[ConfigLoader].from[ConfigLoader.LocalFSImpl]
  make[ConfigLoader.ConfigLocation].from(ConfigLoader.ConfigLocation.Default)
  make[ConfigLoader.Args].from(ConfigLoader.Args.makeConfigLoaderArgs _)
  make[AppConfig].from {
    (configLoader: ConfigLoader) =>
      configLoader.loadConfig()
  }

  make[LoadedPlugins].named("bootstrap").from {
    (loader: PluginLoader @Id("bootstrap"), config: PluginConfig @Id("bootstrap")) =>
      loader.load(config)
  }

  make[LoadedPlugins].named("main").from {
    (loader: PluginLoader @Id("main"), config: PluginConfig @Id("main")) =>
      loader.load(config)
  }

  make[Activation].named("default").fromValue(StandardAxis.prodActivation)
  make[Activation].named("additional").fromValue(Activation.empty)

  make[Boolean].named("distage.roles.reflection").fromValue(true)
  make[Boolean].named("distage.roles.logs.json").fromValue(false)
  make[Boolean].named("distage.roles.ignore-mismatched-effect").fromValue(false)
  make[Boolean].named("distage.roles.activation.ignore-unknown").fromValue(false)
  make[Boolean].named("distage.roles.activation.warn-unset").fromValue(true)

  make[PluginMergeStrategy].named("bootstrap").fromValue(SimplePluginMergeStrategy)
  make[PluginMergeStrategy].named("main").fromValue(SimplePluginMergeStrategy)

  make[ModuleValidator].from[ModuleValidator.ModuleValidatorImpl]

  make[ValidatedModulePair].from {
    (
      validator: ModuleValidator,
      strategy: PluginMergeStrategy @Id("main"),
      plugins: LoadedPlugins @Id("main"),
      bsStrategy: PluginMergeStrategy @Id("bootstrap"),
      bsPlugins: LoadedPlugins @Id("bootstrap"),
    ) =>
      validator.validate(strategy, plugins, bsStrategy, bsPlugins)
  }

  make[ModuleBase].named("main").from((_: ValidatedModulePair).appModule)
  make[ModuleBase].named("bootstrap").from((_: ValidatedModulePair).bootstrapAutoModule)

  make[RoleProvider].from[RoleProvider.Impl]
  make[RolesInfo].from {
    (provider: RoleProvider, appModule: ModuleBase @Id("main"), tagK: TagK[F]) =>
      provider.loadRoles[F](appModule)(tagK)
  }
  make[Set[DIKey]].named("distage.roles.roots").from {
    (rolesInfo: RolesInfo) =>
      rolesInfo.requiredComponents
  }

  make[Set[Axis.AxisChoice]].fromValue(unusedValidAxisChoices)
  make[ActivationChoicesExtractor].from[ActivationChoicesExtractor.Impl]
  make[ActivationInfo].from {
    (activationExtractor: ActivationChoicesExtractor, appModule: ModuleBase @Id("main")) =>
      activationExtractor.findAvailableChoices(appModule)
  }

  make[RoleAppActivationParser].from[RoleAppActivationParser.Impl]
  make[ActivationParser].from[ActivationParser.Impl]
  make[Activation].named("roleapp").from {
    (parser: ActivationParser) =>
      parser.parseActivation()
  }

  make[PlanningOptions].from {
    (parameters: RawAppArgs) =>
      PlanningOptions(
        addGraphVizDump = parameters.globalParameters.hasFlag(RoleAppMain.Options.dumpContext)
      )
  }

  make[Option[LocatorRef]].named("roleapp").from(Some(_: LocatorRef))

  make[ModuleProvider].from[ModuleProvider.Impl[F]]

  make[Module].named("roleapp").from {
    (provider: ModuleProvider, appModule: ModuleBase @Id("main")) =>
      provider.appModules().merge overriddenBy appModule
  }

  make[BootstrapModule].named("roleapp").from {
    (provider: ModuleProvider, bsModule: ModuleBase @Id("bootstrap")) =>
      provider.bootstrapModules().merge overriddenBy bsModule
  }

  make[Bootloader].named("roleapp").from {
    (
      injectorFactory: InjectorFactory,
      bsActivation: Activation @Id("bootstrapActivation"),
      activation: Activation @Id("roleapp"),
      bsModule: BootstrapModule @Id("roleapp"),
      appModule: Module @Id("roleapp"),
      roots: Set[DIKey] @Id("distage.roles.roots"),
      defaultModule: DefaultModule[F],
    ) =>
      injectorFactory.bootloader(bsModule, bsActivation, defaultModule, PlannerInput(appModule, activation, roots))
  }

  make[RoleAppPlanner].from[RoleAppPlanner.Impl[F]]

  make[AppStartupPlans].from {
    (roleAppPlanner: RoleAppPlanner, roots: Set[DIKey] @Id("distage.roles.roots")) =>
      roleAppPlanner.makePlan(roots)
  }

  make[RoleAppEntrypoint[F]].from[RoleAppEntrypoint.Impl[F]]

  make[FinalizerFilters[F]].fromValue(FinalizerFilters.all[F])
  make[AppResourceProvider[F]].from[AppResourceProvider.Impl[F]]
  make[AppResource[F]].from {
    (transformer: AppResourceProvider[F]) =>
      transformer.makeAppResource
  }
}
