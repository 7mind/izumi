package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.CLIParser.*
import izumi.fundamentals.platform.cli.model.*

class CLIParserImpl(mmParser: MultiModalArgsParser, subArgsParser: SubArgsParser) extends CLIParser {

  def parse(args: Array[String]): Either[ParserError, RoleAppArgs] = {
    for {
      mmargs <- mmParser.parse(args)
      primArgs <- subArgsParser.parseSubArgs(mmargs.primaryArgs)

      // TODO: probably we should just remove this code and let role entrypoints to parse args independently
      // We don't want to extend our schema and make it a universal cli->object mapper, and we should let users use any parsers
      modalities = mmargs.modalities
        .map(
          m =>
            subArgsParser
              .parseSubArgs(m.args)
              .map(parsed => (m.id, parsed))
              .merge
        )
      modArgs = modalities.map {
        case (id, params) =>
          RoleArgs(id, params)
      }
      result = RoleAppArgs(primArgs, modArgs)

      _ <- validate(result)

    } yield {
      result
    }
  }

  private def validate(arguments: RoleAppArgs): Either[ParserError, Unit] = {
    val bad = arguments.roles.groupBy(_.role).filter(_._2.size > 1)
    if (bad.nonEmpty) {
      Left(ParserError.DuplicatedRoles(bad.keySet))
    } else {
      Right(())
    }
  }
}
