package com.github.pshirshov.izumi.distage.roles.role2

import com.github.pshirshov.izumi.distage.app.BootstrapConfig
import com.github.pshirshov.izumi.distage.roles.role2.parser.{CLIParser, ParserFailureHandler}
import com.github.pshirshov.izumi.distage.app.services.AppFailureHandler
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.roles.launcher.RoleAppBootstrapStrategy.Using
import distage.TagK


abstract class RoleAppMain[F[_] : TagK : DIEffect](
                            launcher: RoleAppLauncher[F],
                            failureHandler: AppFailureHandler,
                            parserFailureHandler: ParserFailureHandler,
                          ) {
  protected def bootstrapConfig: BootstrapConfig

  def main(args: Array[String]): Unit = {
    try {
      new CLIParser().parse(args) match {
        case Left(parserFailure) =>
          parserFailureHandler.onParserError(parserFailure)

        case Right(parameters) =>
          launcher.launch(parameters, bootstrapConfig, referenceLibraryInfo)
      }
    } catch {
      case t: Throwable =>
        failureHandler.onError(t)
    }
  }

  protected def referenceLibraryInfo: Seq[Using] = Seq.empty
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
