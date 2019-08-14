package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.services.AppFailureHandler
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.{RawAppArgs, RawRoleParams}
import com.github.pshirshov.izumi.fundamentals.platform.cli.{CLIParser, ParserFailureHandler}
import distage.TagK


abstract class RoleAppMain[F[_] : TagK : DIEffect](
                                                    launcher: RoleAppLauncher[F],
                                                    failureHandler: AppFailureHandler,
                                                    parserFailureHandler: ParserFailureHandler,
                                                  ) {

  def main(args: Array[String]): Unit = {
    try {
      parse(args) match {
        case Left(parserFailure) =>
          parserFailureHandler.onParserError(parserFailure)

        case Right(parameters) =>
          launcher.launch(parameters.copy(roles = requiredRoles ++ parameters.roles))
      }
    } catch {
      case t: Throwable =>
        failureHandler.onError(t)
    }
  }

  protected def parse(args: Array[String]): Either[CLIParser.ParserError, RawAppArgs] = new CLIParser().parse(args)

  protected def requiredRoles: Vector[RawRoleParams] = Vector.empty

}

object RoleAppMain {

  abstract class Default[F[_] : TagK : DIEffect](launcher: RoleAppLauncher[F])
    extends RoleAppMain(
      launcher,
      AppFailureHandler.TerminatingHandler,
      ParserFailureHandler.TerminatingHandler,
    )

  abstract class Safe[F[_] : TagK : DIEffect](launcher: RoleAppLauncher[F])
    extends RoleAppMain(
      launcher,
      AppFailureHandler.PrintingHandler,
      ParserFailureHandler.PrintingHandler
    )

  abstract class Silent[F[_] : TagK : DIEffect](launcher: RoleAppLauncher[F])
    extends RoleAppMain(
      launcher,
      AppFailureHandler.NullHandler,
      ParserFailureHandler.NullHandler
    )

}

