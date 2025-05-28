package izumi.fundamentals.platform.cli

import izumi.fundamentals.platform.cli.model.{EntrypointArgs, RawFlag, RawValue}

trait SubArgsParser {
  def parseSubArgs(args: Vector[String]): Either[Nothing, EntrypointArgs]

}

class SubArgsParserImpl() extends SubArgsParser {
  sealed trait Arg
  object Arg {
    case class Separator() extends Arg
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
          Arg.Separator()
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
        case Arg.Separator() =>
          new StDontParse(raw, flags, parameters, freeArgs)
      }

    }
  }

  class StDontParse(raw: Vector[String], flags: Vector[RawFlag], parameters: Vector[RawValue], freeArgs: Vector[String]) extends State {
    override def freeze(): Either[Nothing, EntrypointArgs] = {
      Right(EntrypointArgs(raw, flags, parameters, freeArgs))
    }

    override def next(arg: String): State = {
      new StDontParse(raw, flags, parameters, freeArgs :+ arg)
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
        case Arg.Separator() =>
          new StDontParse(raw, flags :+ flag, parameters, freeArgs)
      }
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
}
