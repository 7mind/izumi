package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.CLIParser.ParserError
import izumi.fundamentals.platform.cli.CLIParser.ParserError.{DanglingArgument, DanglingSplitter, DuplicatedRoles}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.language.Quirks

trait ParserFailureHandler {
  def onParserError(e: ParserError): RawAppArgs
}

object ParserFailureHandler {

  object TerminatingHandler extends ParserFailureHandler {
    override def onParserError(e: ParserError): Nothing = {
      System.err.println(makeMessage(e))
      System.exit(1)
      throw new IllegalStateException("System.exit() didn't work")
    }
  }

  object PrintingHandler extends ParserFailureHandler {
    override def onParserError(e: ParserError): RawAppArgs = {
      System.err.println(makeMessage(e))
      RawAppArgs.empty
    }
  }

  object NullHandler extends ParserFailureHandler {
    override def onParserError(e: ParserError): RawAppArgs = {
      Quirks.discard(e)
      RawAppArgs.empty
    }
  }

  private[this] def makeMessage(e: ParserError): String = {
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

  final val example =
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
       |Example:
       |
       |  -with-flag --option=value :my-role
       |  -with-flag --option=value :my-role --role-config=myconfig.json :other-role
       |  -with-flag --option=value :first-role --role-config=myconfig.json -- --role-option=value role args here \\
       |    :convert -- input.txt output.jpg
       |
       |Try ':help' to get application specific help
     """.stripMargin

}
