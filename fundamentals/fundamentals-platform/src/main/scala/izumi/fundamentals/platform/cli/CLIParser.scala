package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.CLIParser.ParserError
import izumi.fundamentals.platform.cli.model.{MultiModalArgs, RoleAppArgs}

trait CLIParser {
  def parse(args: Array[String]): Either[ParserError, RoleAppArgs]
}

object CLIParser {
  sealed trait ParserError
  object ParserError {
    final case class DuplicatedRoles(bad: Set[String]) extends ParserError
  }
}

/** Splits multi-modal command line a1 -a2 ... :name1 -m11 -m12 ... :name2 -m21 -m22 ...)
  * This parser cannot fail.
  */
trait MultiModalArgsParser {
  def parse(args: Array[String]): Either[Nothing, MultiModalArgs]
}
