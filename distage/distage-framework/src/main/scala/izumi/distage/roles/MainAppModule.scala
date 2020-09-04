package izumi.distage.roles

import distage.{DIResourceBase, Id, ModuleDef, StandardAxis, TagK}
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.services.ConfigLoader
import izumi.distage.model.definition.Activation
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.{PluginLoader, PluginLoaderDefaultImpl}
import izumi.distage.roles.RoleAppMain.{AdditionalRoles, ArgV}
import izumi.distage.roles.launcher.services.EarlyLoggers
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
  make[PluginConfig].fromValue(pluginConfig)

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

  make[Activation].named("main").fromValue(StandardAxis.prodActivation)
  make[Activation].named("additional").fromValue(Activation.empty)
//
//  make[PluginLoader].named("main").from[PluginLoaderDefaultImpl]

  make[DIResourceBase[Identity, PreparedApp[F]]].from {
    (launcher: RoleAppLauncher[F], args: RawAppArgs) =>
      launcher.launch(args)
  }
}
