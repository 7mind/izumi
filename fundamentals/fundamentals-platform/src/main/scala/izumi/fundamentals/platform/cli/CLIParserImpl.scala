package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.CLIParser.*
import izumi.fundamentals.platform.cli.model.*

class CLIParserImpl(mmParser: MultiModalArgsParser) extends CLIParser {

  sealed trait Arg
  object Arg {
    case class Free(value: String) extends Arg
    case class Value(name: String, value: String) extends Arg
    case class Flag(name: String) extends Arg
  }

  trait State {
    def freeze(): Either[Nothing, EntrypointArgs]
    def next(arg: String): State

    final def parseArg(arg: String): Arg = {
      if (arg.startsWith("--")) {
        if (arg.length > 2) {
          val argName = arg.substring(2)
          argName.indexOf('=') match {
            case -1 =>
              Arg.Flag(argName)
            case pos =>
              val (k, v) = argName.splitAt(pos)
              Arg.Value(k, v.substring(1))
          }
        } else {
          Arg.Free(arg)
        }
      } else if (arg.startsWith("-")) {
        val paramName = arg.substring(1)
        Arg.Flag(paramName)
      } else {
        Arg.Free(arg)
      }
    }
  }

  class StInitial(raw: Vector[String], flags: Vector[RawFlag], parameters: Vector[RawValue], freeArgs: Vector[String]) extends State {
    override def freeze(): Either[Nothing, EntrypointArgs] = {
      Right(EntrypointArgs(raw, flags, parameters, freeArgs))
    }

    override def next(arg: String): State = {
      parseArg(arg) match {
        case Arg.Free(value) =>
          new StInitial(raw, flags, parameters, freeArgs :+ value)
        case Arg.Value(name, value) =>
          new StInitial(raw, flags, parameters :+ RawValue(name, value), freeArgs)
        case Arg.Flag(name) =>
          new StFlagOpen(raw, RawFlag(name), flags, parameters, freeArgs)
      }

    }
  }

  class StFlagOpen(raw: Vector[String], flag: RawFlag, flags: Vector[RawFlag], parameters: Vector[RawValue], freeArgs: Vector[String]) extends State {
    override def freeze(): Either[Nothing, EntrypointArgs] = {
      Right(EntrypointArgs(raw, flags :+ flag, parameters, freeArgs))
    }

    override def next(arg: String): State = {
      parseArg(arg) match {
        case Arg.Free(value) =>
          new StInitial(raw, flags, parameters :+ RawValue(flag.name, value), freeArgs)
        case Arg.Value(name, value) =>
          new StInitial(raw, flags :+ flag, parameters :+ RawValue(name, value), freeArgs)
        case Arg.Flag(name) =>
          new StFlagOpen(raw, RawFlag(name), flags :+ flag, parameters, freeArgs)
      }
    }
  }

  def parse(args: Array[String]): Either[ParserError, RoleAppArgs] = {
    for {
      mmargs <- mmParser.parse(args)
      primArgs <- parseSubArgs(mmargs.primaryArgs)

      // TODO: probably we should just remove this code and let role entrypoints to parse args independently
      modalities = mmargs.modalities
        .map(
          m =>
            parseSubArgs(m.args)
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

  def parseSubArgs(args: Vector[String]): Either[Nothing, EntrypointArgs] = {
    args
      .foldLeft(new StInitial(args, Vector.empty, Vector.empty, Vector.empty): State) {
        case (s, a) =>
          s.next(a)
      }
      .freeze()
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
