package izumi.distage.roles

import distage.config.AppConfig
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.AppArgsInterceptor
import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RequiredRoles}
import izumi.fundamentals.platform.cli.{CLIParser, ParserFailureHandler}
import izumi.reflect.TagK
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.roles.launcher.*
import izumi.distage.model.definition.Activation

class RoleAppBootArgsModule[F[_]: TagK: DefaultModule](
  args: ArgV,
  requiredRoles: RequiredRoles,
) extends ModuleDef {
  make[ArgV].fromValue(args)
  make[RequiredRoles].fromValue(requiredRoles)
  make[RawAppArgs].from {
    (parser: CLIParser, args: ArgV, handler: ParserFailureHandler, interceptor: AppArgsInterceptor, additionalRoles: RequiredRoles) =>
      parser.parse(args.args) match {
        case Left(error) =>
          handler.onParserError(error)
        case Right(args) =>
          interceptor.rolesToLaunch(args, additionalRoles)
      }
  }

  // TODO: stuff below not stubbed for js
  make[PlanningOptions].from {
    (parameters: RawAppArgs) =>
      PlanningOptions(
        addGraphVizDump = parameters.globalParameters.hasFlag(RoleAppMain.Options.dumpContext)
      )
  }

  make[RoleAppActivationParser].from[RoleAppActivationParser.Impl]
  make[ActivationParser].from[ActivationParser.Impl]
  make[Activation].named("roleapp").from {
    (parser: ActivationParser, config: AppConfig) =>
      parser.parseActivation(config)
  }

  make[AppArgsInterceptor].from[AppArgsInterceptor.Impl]

  make[CLILoggerOptionsReader].from[CLILoggerOptionsReader.CLILoggerOptionsReaderImpl]
  make[CLILoggerOptions].from {
    (reader: CLILoggerOptionsReader) =>
      reader.read()
  }
}
