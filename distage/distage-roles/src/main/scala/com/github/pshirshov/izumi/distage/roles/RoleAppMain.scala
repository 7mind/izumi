package com.github.pshirshov.izumi.distage.roles

import com.github.pshirshov.izumi.distage.app.BootstrapConfig
import com.github.pshirshov.izumi.distage.app.services.AppFailureHandler
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.cli.{RoleAppArguments, RoleArg}
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import com.github.pshirshov.izumi.distage.roles.role2.parser.ParserFailureHandler
import com.github.pshirshov.izumi.distage.roles.services.cliparser.{CLIParser, ParserFailureHandler}
import distage.TagK


abstract class RoleAppMain[F[_] : TagK : DIEffect](
                                                    launcher: RoleAppLauncher[F],
                                                    failureHandler: AppFailureHandler,
                                                    parserFailureHandler: ParserFailureHandler,
                                                  ) {
  protected def bootstrapConfig: BootstrapConfig

  def main(args: Array[String]): Unit = {
    try {
      parse(args) match {
        case Left(parserFailure) =>
          parserFailureHandler.onParserError(parserFailure)

        case Right(parameters) =>
          launcher.launch(parameters.copy(roles = requiredRoles ++ parameters.roles), bootstrapConfig, referenceLibraryInfo)
      }
    } catch {
      case t: Throwable =>
        failureHandler.onError(t)
    }
  }

  protected def parse(args: Array[String]): Either[CLIParser.ParserError, RoleAppArguments] = new CLIParser().parse(args)

  protected def requiredRoles: Vector[RoleArg] = Vector.empty

  protected def referenceLibraryInfo: Seq[Using] = Vector.empty
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

  abstract class Slient[F[_] : TagK : DIEffect](launcher: RoleAppLauncher[F])
    extends RoleAppMain(
      launcher,
      AppFailureHandler.NullHandler,
      ParserFailureHandler.NullHandler
    )

}

//object L extends RoleAppMain.Default[IO]()
