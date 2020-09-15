package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.CLIParser._
import izumi.fundamentals.platform.cli.impl.CLIParserState
import izumi.fundamentals.platform.cli.model.raw._

import scala.collection.mutable

class CLIParserImpl extends CLIParser {

  def parse(args: Array[String]): Either[ParserError, RawAppArgs] = {
    var state: CLIParserState = new CLIParserState.Initial()
    val processed = mutable.ArrayBuffer[String]()
    args.foreach {
      arg =>
        state = if (arg.startsWith(":") && arg.length > 1) {
          state.addRole(arg.substring(1))
        } else if (arg.startsWith("--") && arg.length > 2) {
          val argv = arg.substring(2)
          argv.indexOf('=') match {
            case -1 =>
              state.addFlag(arg)(RawFlag(argv))
            case pos =>
              val (k, v) = argv.splitAt(pos)
              state.addParameter(arg)(RawValue(k, v.substring(1)))
          }
        } else if (arg == "--") {
          state.splitter(processed.toVector)
        } else if (arg.startsWith("-")) {
          val argv = arg.substring(1)
          state.openParameter(arg)(argv)
        } else {
          state.addFreeArg(processed.toVector)(arg)
        }
        processed += arg
    }

    for {
      roles <- state.freeze()
      _ <- validate(roles)
    } yield roles
  }

  private[this] def validate(arguments: RawAppArgs): Either[ParserError, Unit] = {
    val bad = arguments.roles.groupBy(_.role).filter(_._2.size > 1)
    if (bad.nonEmpty) {
      Left(ParserError.DuplicatedRoles(bad.keySet))
    } else {
      Right(())
    }
  }
}
