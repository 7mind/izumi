package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.CLIParser.*
import izumi.fundamentals.platform.cli.model.*

class CLIParserImpl(mmParser: MultiModalArgsParser, subArgsParser: SubArgsParser) extends CLIParser {

  def parse(args: Array[String]): Either[ParserError, RoleAppArgs] = {
    for {
      mmargs <- mmParser.parse(args)
      primArgs <- subArgsParser.parseSubArgs(mmargs.primaryArgs)

      // Probably we should just remove this code and let role entrypoints to parse args independently
      // Current issues:
      // 1) Each role has to do parsing and reporting on its own, failures would look ugly
      // 2) If a user wants to use their own parser/object mapper, they still receive parsed EntrypointArgs which they don't need
      // Potential improvements:
      // - Add parsers/implementations as type parameters/fields in role descriptors
      // - Change def start(roleParameters: EntrypointArgs) signature to def start(roleParameters: ARG), where ARG is a type parameter of the role
      // Not sure if it's really beneficial though

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
