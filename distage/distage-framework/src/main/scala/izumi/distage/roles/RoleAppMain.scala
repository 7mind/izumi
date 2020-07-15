package izumi.distage.roles

import izumi.distage.roles.services.AppFailureHandler
import izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawRoleParams}
import izumi.fundamentals.platform.cli.{CLIParser, ParserFailureHandler}

abstract class RoleAppMain(
  launcher: RoleAppLauncher,
  failureHandler: AppFailureHandler,
  parserFailureHandler: ParserFailureHandler,
) {
  protected def parse(args: Array[String]): Either[CLIParser.ParserError, RawAppArgs] = new CLIParser().parse(args)
  protected def requiredRoles: Vector[RawRoleParams] = Vector.empty

  def main(args: Array[String]): Unit = {
    try {
      parse(args) match {
        case Left(parserFailure) =>
          parserFailureHandler.onParserError(parserFailure)

        case Right(parameters) =>
          val requestedRoles = parameters.roles
          val requestedRoleSet = requestedRoles.map(_.role).toSet
          val knownRequiredRoles = requiredRoles.filterNot(requestedRoleSet contains _.role)
          launcher.launch(parameters.copy(roles = rolesToLaunch(requestedRoles, knownRequiredRoles)))
      }
    } catch {
      case t: Throwable =>
        failureHandler.onError(t)
    }
  }

  protected def rolesToLaunch(requestedRoles: Vector[RawRoleParams], knownRequiredRoles: Vector[RawRoleParams]): Vector[RawRoleParams] = {
    knownRequiredRoles ++ requestedRoles
  }
}

object RoleAppMain {

  class Default(launcher: RoleAppLauncher)
    extends RoleAppMain(
      launcher,
      AppFailureHandler.TerminatingHandler,
      ParserFailureHandler.TerminatingHandler,
    )

  class Safe(launcher: RoleAppLauncher)
    extends RoleAppMain(
      launcher,
      AppFailureHandler.PrintingHandler,
      ParserFailureHandler.PrintingHandler,
    )

  class Silent(launcher: RoleAppLauncher)
    extends RoleAppMain(
      launcher,
      AppFailureHandler.NullHandler,
      ParserFailureHandler.NullHandler,
    )

}
