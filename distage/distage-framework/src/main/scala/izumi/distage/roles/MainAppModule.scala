package izumi.distage.roles

import izumi.distage.InjectorFactory
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.framework.services._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition._
import izumi.distage.model.recursive.Bootloader
import izumi.distage.model.reflection.DIKey
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.load.{PluginLoader, PluginLoaderDefaultImpl}
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.plugins.{PluginBase, PluginConfig}
import izumi.distage.roles.RoleAppMain.{AdditionalRoles, ArgV}
import izumi.distage.roles.launcher.AppResourceProvider.FinalizerFilters
import izumi.distage.roles.launcher.ModuleValidator.ValidatedModulePair
import izumi.distage.roles.launcher._
import izumi.distage.roles.model.meta.{LibraryReference, RolesInfo}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.cli.{CLIParser, CLIParserImpl, ParserFailureHandler}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.resources.IzArtifact
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.api.{IzLogger, Log}
import izumi.reflect.TagK

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
  * 9. Build plan for [[izumi.distage.model.effect.QuasiIORunner]]
  * 10. Build plan for integration checks
  * 11. Build plan for application
  * 12. Run role tasks
  * 13. Run role services
  * 14. Await application termination
  * 15. Run finalizers
  * 16. Shutdown executors
  */
class MainAppModule[F[_]: TagK: DefaultModule](
  args: ArgV,
  additionalRoles: AdditionalRoles,
  shutdownStrategy: AppShutdownStrategy[F],
  pluginConfig: PluginConfig,
  bootstrapPluginConfig: PluginConfig,
  appArtifact: IzArtifact,
) extends ModuleDef {
  addImplicit[TagK[F]]
  addImplicit[DefaultModule[F]]

  make[ArgV].fromValue(args)
  make[AdditionalRoles].fromValue(additionalRoles)

  make[AppShutdownStrategy[F]].fromValue(shutdownStrategy)
  make[PluginConfig].named("main").fromValue(pluginConfig)
  make[PluginConfig].named("bootstrap").fromValue(bootstrapPluginConfig)

  make[Option[IzArtifact]].named("app.artifact").fromValue(Some(appArtifact))

  make[CLIParser].from[CLIParserImpl]
  make[ParserFailureHandler].from(ParserFailureHandler.TerminatingHandler)
  make[AppArgsInterceptor].from[AppArgsInterceptor.AppArgsInterceptorImpl]

  make[RawAppArgs].from {
    (parser: CLIParser, args: ArgV, handler: ParserFailureHandler, interceptor: AppArgsInterceptor, additionalRoles: AdditionalRoles) =>
      parser.parse(args.args) match {
        case Left(value) =>
          handler.onParserError(value)
        case Right(value) =>
          interceptor.rolesToLaunch(value, additionalRoles)
      }
  }

  many[LibraryReference]

  make[Log.Level].named("early").fromValue(Log.Level.Info)
  make[StartupBanner].from[StartupBanner.Impl]
  make[IzLogger].named("early").from {
    (parameters: RawAppArgs, defaultLogLevel: Log.Level @Id("early"), banner: StartupBanner) =>
      val logger = EarlyLoggers.makeEarlyLogger(parameters, defaultLogLevel)
      banner.showBanner(logger)
      logger
  }

  make[PluginLoader]
    .named("bootstrap")
    .aliased[PluginLoader]("main")
    .from[PluginLoaderDefaultImpl]

  make[ConfigLoader].from[ConfigLoader.LocalFSImpl]
  make[ConfigLoader.Args].from(ConfigLoader.Args.makeConfigLoaderParameters _)
  make[AppConfig].from {
    configLoader: ConfigLoader =>
      configLoader.loadConfig()
  }

  make[Seq[PluginBase]]
    .named("bootstrap")
    .from {
      (loader: PluginLoader @Id("bootstrap"), config: PluginConfig @Id("bootstrap")) =>
        loader.load(config)
    }

  make[Seq[PluginBase]]
    .named("main").from {
      (loader: PluginLoader @Id("main"), config: PluginConfig @Id("main")) =>
        loader.load(config)
    }

  make[Activation].named("main").fromValue(StandardAxis.prodActivation)
  make[Activation].named("additional").fromValue(Activation.empty)

  make[Boolean].named("distage.roles.reflection").fromValue(true)
  make[Boolean].named("distage.roles.logs.json").fromValue(false)

  make[IzLogger].from {
    (
      parameters: RawAppArgs,
      earlyLogger: IzLogger @Id("early"),
      config: AppConfig,
      defaultLogLevel: Log.Level @Id("early"),
      defaultLogFormatJson: Boolean @Id("distage.roles.logs.json"),
    ) =>
      EarlyLoggers.makeLateLogger(parameters, earlyLogger, config, defaultLogLevel, defaultLogFormatJson)
  }
  make[LogRouter].from((_: IzLogger).router)

  make[PluginMergeStrategy].named("bootstrap").fromValue(SimplePluginMergeStrategy)
  make[PluginMergeStrategy].named("main").fromValue(SimplePluginMergeStrategy)

  make[ModuleValidator].from[ModuleValidator.ModuleValidatorImpl]

  make[ValidatedModulePair].from {
    (
      validator: ModuleValidator,
      strategy: PluginMergeStrategy @Id("main"),
      plugins: Seq[PluginBase] @Id("main"),
      bsStrategy: PluginMergeStrategy @Id("bootstrap"),
      bsPlugins: Seq[PluginBase] @Id("bootstrap"),
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
    rolesInfo: RolesInfo =>
      rolesInfo.requiredComponents
  }

  make[ActivationChoicesExtractor].from[ActivationChoicesExtractor.Impl]
  make[ActivationInfo].from {
    (activationExtractor: ActivationChoicesExtractor, appModule: ModuleBase @Id("main")) =>
      activationExtractor.findAvailableChoices(appModule)
  }

  make[ActivationParser].from[ActivationParser.Impl]
  make[Activation].named("primary").from {
    parser: ActivationParser =>
      parser.parseActivation()
  }

  make[PlanningOptions].from {
    parameters: RawAppArgs =>
      PlanningOptions(
        addGraphVizDump = parameters.globalParameters.hasFlag(RoleAppMain.Options.dumpContext)
      )
  }

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
      activation: Activation @Id("primary"),
      finalAppModule: Module @Id("roleapp"),
      finalBsModule: BootstrapModule @Id("roleapp"),
      roots: Set[DIKey] @Id("distage.roles.roots"),
      defaultModule: DefaultModule[F],
    ) =>
      // // `bsModule` is set to empty because it's always overridden to `BootstrapModule @Id("roleapp")` by `RoleAppPlanner#makePlan`
      // // Same for `roots`
      injectorFactory.bootloader(PlannerInput(finalAppModule, activation, roots), activation, finalBsModule, defaultModule)
  }

  make[RoleAppPlanner[F]].from[RoleAppPlanner.Impl[F]]

  make[AppStartupPlans].from {
    (planner: RoleAppPlanner[F], roots: Set[DIKey] @Id("distage.roles.roots")) =>
      planner.makePlan(roots)
  }

  make[IntegrationChecker[F]].from[IntegrationChecker.Impl[F]]
  make[RoleAppEntrypoint[F]].from[RoleAppEntrypoint.Impl[F]]

  make[FinalizerFilters[F]].fromValue(FinalizerFilters.all[F])
  make[AppResourceProvider[F]].from[AppResourceProvider.Impl[F]]
  make[Lifecycle[Identity, PreparedApp[F]]].from {
    transformer: AppResourceProvider[F] =>
      transformer.makeAppResource()
  }
}
