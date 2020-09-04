package izumi.distage.roles

import distage.{DIResourceBase, ModuleDef, TagK}
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.{AdditionalRoles, ArgV}
import izumi.distage.roles.launcher.{AppShutdownStrategy, RoleAppLauncher, RoleAppLauncherImpl}
import izumi.distage.roles.launcher.services.StartupPlanExecutor.PreparedApp
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.cli.{CLIParser, CLIParserImpl, ParserFailureHandler}
import izumi.fundamentals.platform.functional.Identity
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

//make[RoleAppLauncher[F]].fromValue(launcher)
//  make[RoleAppLauncher[F]].fromValue(??? : RoleAppLauncherImpl[F]) //.from[RoleAppLauncherImpl[F]]
  make[RoleAppLauncher[F]].from[RoleAppLauncherImpl[F]]

  make[DIResourceBase[Identity, PreparedApp[F]]].from {
    (launcher: RoleAppLauncher[F], args: RawAppArgs) =>
      launcher.launch(args)
  }
}
