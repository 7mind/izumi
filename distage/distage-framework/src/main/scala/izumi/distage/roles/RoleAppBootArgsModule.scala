package izumi.distage.roles

import distage.config.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.definition.{Activation, ModuleDef}
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.launcher.*
import izumi.fundamentals.platform.cli.model.{RequiredRoles, RoleAppArgs}
import izumi.fundamentals.platform.cli.{CLIParser, ParserFailureHandler}

class RoleAppBootArgsModule(
  args: ArgV,
  requiredRoles: RequiredRoles,
) extends ModuleDef
  with RoleAppBootArgsModulePlatformSpecific {
  make[ArgV].fromValue(args)
  make[RequiredRoles].fromValue(requiredRoles)
  make[RoleAppArgs].from {
    (parser: CLIParser, args: ArgV, handler: ParserFailureHandler, interceptor: AppArgsInterceptor, additionalRoles: RequiredRoles) =>
      parser.parse(args.args) match {
        case Left(error) =>
          handler.onParserError(error)
        case Right(args) =>
          interceptor.rolesToLaunch(args, additionalRoles)
      }
  }

  make[PlanningOptions].from {
    mkPlanningOptionsPlatformSpecific
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
