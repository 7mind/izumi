package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.CLIParser.ParserError
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs

trait CLIParser {
  def parse(args: Array[String]): Either[ParserError, RawAppArgs]
}

object CLIParser {

  sealed trait ParserError

  object ParserError {

    final case class DanglingArgument(processed: Vector[String], arg: String) extends ParserError

    final case class DanglingSplitter(processed: Vector[String]) extends ParserError

    final case class DuplicatedRoles(bad: Set[String]) extends ParserError

  }

}
