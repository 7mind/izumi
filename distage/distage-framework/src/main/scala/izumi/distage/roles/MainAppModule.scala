package izumi.distage.roles

import distage.{DIResourceBase, Id, ModuleDef, StandardAxis, TagK}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.Activation
import izumi.distage.plugins.{PluginBase, PluginConfig}
import izumi.distage.plugins.load.{PluginLoader, PluginLoaderDefaultImpl}
import izumi.distage.roles.RoleAppMain.{AdditionalRoles, ArgV}
import izumi.distage.roles.launcher.services.{EarlyLoggers, RoleProvider}
import izumi.distage.roles.launcher.{AppShutdownStrategy, RoleAppLauncher, RoleAppLauncherImpl}
import izumi.distage.roles.launcher.services.StartupPlanExecutor.PreparedApp
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.cli.{CLIParser, CLIParserImpl, ParserFailureHandler}
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.{IzLogger, Log}
import izumi.reflect.Tag

class MainAppModule[F[_]: TagK](
  args: ArgV,
  additionalRoles: AdditionalRoles,
  shutdownStrategy: AppShutdownStrategy[F],
  pluginConfig: PluginConfig,
)(implicit t: Tag[TagK[F]]
) extends ModuleDef {
  make[ArgV].fromValue(args)
  make[CLIParser].from[CLIParserImpl]
  make[AdditionalRoles].fromValue(additionalRoles)
  make[ParserFailureHandler].from[ParserFailureHandler.TerminatingHandler.type]
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

  addImplicit[TagK[F]]

  make[AppShutdownStrategy[F]].fromValue(shutdownStrategy)
  make[PluginConfig]
    .named("main")
    .fromValue(pluginConfig)

  make[RoleAppLauncher[F]].from[RoleAppLauncherImpl[F]]

  make[Log.Level].named("early").fromValue(Log.Level.Info)

  //make[IzLogger].named("early").from(EarlyLoggers.makeEarlyLogger _)
  make[IzLogger].named("early").from {
    (parameters: RawAppArgs, defaultLogLevel: Log.Level @Id("early")) =>
      EarlyLoggers.makeEarlyLogger(parameters, defaultLogLevel)
  }

  make[PluginConfig].named("bootstrap").fromValue(PluginConfig.empty)
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
    .named("main")
    .from {
      (loader: PluginLoader @Id("main"), config: PluginConfig @Id("main")) =>
        loader.load(config)
    }

  make[Activation].named("main").fromValue(StandardAxis.prodActivation)
  make[Activation].named("additional").fromValue(Activation.empty)

//
//  make[PluginLoader].named("main").from[PluginLoaderDefaultImpl]

  make[Boolean].named("distage.roles.reflection").fromValue(true)
  make[Boolean].named("distage.roles.logs.json").fromValue(false)
  make[RoleProvider[F]].from[RoleProvider.Impl[F]]

  make[IzLogger].from {
    (
      parameters: RawAppArgs,
      defaultLogLevel: Log.Level @Id("early"),
      earlyLogger: IzLogger @Id("early"),
      config: AppConfig,
      defaultLogFormatJson: Boolean @Id("distage.roles.logs.json"),
    ) =>
      EarlyLoggers.makeLateLogger(parameters, earlyLogger, config, defaultLogLevel, defaultLogFormatJson)
  }

  make[DIResourceBase[Identity, PreparedApp[F]]].from {
    (launcher: RoleAppLauncher[F], args: RawAppArgs) =>
      launcher.launch(args)
  }
}
