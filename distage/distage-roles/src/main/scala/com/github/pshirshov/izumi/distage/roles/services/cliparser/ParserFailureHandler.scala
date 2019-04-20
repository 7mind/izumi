package com.github.pshirshov.izumi.distage.roles.services.cliparser

import com.github.pshirshov.izumi.distage.roles.RoleAppLauncher
import com.github.pshirshov.izumi.distage.roles.services.cliparser.CLIParser.ParserError
import com.github.pshirshov.izumi.distage.roles.services.cliparser.CLIParser.ParserError.{DanglingArgument, DanglingSplitter, DuplicatedRoles}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

trait ParserFailureHandler {
  def onParserError(e: CLIParser.ParserError): Unit
}

object ParserFailureHandler {

  object TerminatingHandler extends ParserFailureHandler {
    override def onParserError(e: CLIParser.ParserError): Unit = {
      System.err.println(makeMessage(e))
      System.exit(1)
    }
  }

  object PrintingHandler extends ParserFailureHandler {
    override def onParserError(e: CLIParser.ParserError): Unit = {
      System.err.println(makeMessage(e))
    }
  }

  object NullHandler extends ParserFailureHandler {
    override def onParserError(e: CLIParser.ParserError): Unit = {
      Quirks.discard(e)
    }
  }

  private def makeMessage(e: ParserError): String = {
    e match {
      case d: DanglingArgument =>
        s"""Improperly positioned argument '${d.arg}' after valid commandline '${d.processed.mkString(" ")}'
           |
           |$example
         """.stripMargin
      case d: DanglingSplitter =>
        s"""Improperly positioned splitter '--' after valid commandline '${d.processed.mkString(" ")}'
           |
           |$example
         """.stripMargin
      case d: DuplicatedRoles =>
        s"""Duplicated roles: ${d.bad.mkString(", ")}
           |
           |$example
         """.stripMargin
    }
  }

  private final val example =
    s"""General commandline format:
       |
       |  [OPTION...] [ROLE...]
       |
       |    OPTION is one of the following:
       |
       |      --flag-name
       |      --value-name=value
       |      -flag
       |      -shortname value
       |
       |    ROLE is :role-name [OPTION...] [--] [<role-argument>...]
       |
       |Examples:
       |
       |    --log-format=json :my-role
       |    --log-format=json :dump --role-config=myconfig.json -- --target-file=input.txt :convert -- input.txt output.jpg
       |
       |
       |Common global options are:
       |
       |  ${RoleAppLauncher.logLevelRootParam}={trace|debug|info|warning|error|critical}
       |  ${RoleAppLauncher.logFormatParam}={json|text}
       |
       |Common role options are:
       |
       |  -rc, --role-config=<config-name>
       |
       |Try '--help' or ':help' to get more information.
     """.stripMargin

}
