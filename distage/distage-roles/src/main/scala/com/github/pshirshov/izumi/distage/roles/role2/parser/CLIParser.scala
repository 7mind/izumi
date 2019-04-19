package com.github.pshirshov.izumi.distage.roles.role2.parser
import com.github.pshirshov.izumi.distage.roles.cli._
import com.github.pshirshov.izumi.distage.roles.role2.parser.CLIParser._
object CLIParser {

  case class ParameterNameDef private (long: String, short: Option[String]) {
    def all: Set[String] = Set(long) ++ short.toSet
    def matches(name: String): Boolean = all.contains(name)

    def format: String = {
      short match {
        case Some(value) =>
          s"$value, $long"
        case None =>
          s"$long"
      }
    }
  }


  object ParameterNameDef {
    def apply(long: String, short: String): ParameterNameDef = new ParameterNameDef(long, Some(short))
    def apply(long: String): ParameterNameDef = new ParameterNameDef(long, None)
  }

  sealed trait ParserError
  object ParserError {

    final case class DanglingArgument(processed: Vector[String], arg: String) extends ParserError
    final case class DanglingSplitter(processed: Vector[String]) extends ParserError
    final case class DuplicatedRoles(bad: Set[String]) extends ParserError

  }

  def main(args: Array[String]): Unit = {
    println(new CLIParser().parse(Array("--help", "--x=y", "--logs=json", ":role1", "--config=xxx", "arg1", "arg2", ":role2")))
    println(new CLIParser().parse(Array("--help", "--x=y", "--logs=json", ":role1", "--config=xxx", "arg1", "arg2", "--yyy=zzz", ":role2")))
    println(new CLIParser().parse(Array("--help", "--x=y", "--logs=json", ":role1", "--", "--config=xxx", "arg1", "arg2", "--yyy=zzz", ":role2")))
  }
}



import scala.collection.mutable

class CLIParser {
  def parse(args: Array[String]): Either[ParserError, RoleAppArguments] = {
    var state: CLIParserState = new CLIParserState.Initial()
    val processed = mutable.ArrayBuffer[String]()
    args.foreach {
      arg =>
        state = if (arg.startsWith(":") && arg.length > 1) {
          state.addRole(arg.substring(1))
        } else if (arg.startsWith("--") && arg.length > 2) {
          val argv = arg.substring(2)
          arg.indexOf('=') match {
            case -1 =>
              state.addFlag(arg)(Flag(argv))
            case pos =>
              val (k, v) = arg.splitAt(pos)
              state.addParameter(arg)(Value(k, v.substring(1)))
          }
        } else if (arg.startsWith("-")) {
          ???
        } else if (arg == "--") {
          state.splitter(processed.toVector)
        } else {
          state.addFreeArg(processed.toVector)(arg)
        }
        processed += arg
    }

    for {
      roles <- state.freeze()
      _ <- validate(roles)
    } yield {
      roles
    }
  }

  private def validate(arguments: RoleAppArguments): Either[ParserError, Unit] = {
    val bad = arguments.roles.groupBy(_.role).filter(_._2.size > 1)
    if (bad.nonEmpty) {
      Left(ParserError.DuplicatedRoles(bad.keySet))
    } else {
      Right(())
    }
  }
}

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

sealed trait CLIParserState {
  def addRole(name: String): CLIParserState
  def addFlag(rawArg: String)(flag: Flag): CLIParserState
  def addParameter(rawArg: String)(parameter: Value): CLIParserState
  def addFreeArg(processed: Vector[String])(arg: String): CLIParserState
  def splitter(processed: Vector[String])(): CLIParserState
  def freeze(): Either[ParserError, RoleAppArguments]
}


object CLIParserState {

  class Error(error: ParserError) extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      Quirks.discard(name)
      this
    }

    override def addFlag(rawArg: String)(flag: Flag): CLIParserState = {
      Quirks.discard(rawArg, flag)
      this
    }

    override def addParameter(rawArg: String)(parameter: Value): CLIParserState = {
      Quirks.discard(rawArg, parameter)
      this
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      Quirks.discard(arg, processed)
      this
    }

    override def splitter(processed: Vector[String])(): CLIParserState = {
      Quirks.discard(processed)
      this
    }

    override def freeze(): Either[ParserError, RoleAppArguments] = {
      Left(error)
    }
  }

  class Initial() extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      new RoleParameters(Parameters.empty, Vector.empty, RoleArg(name, Parameters.empty, Vector.empty))
    }

    override def addFlag(rawArg: String)(flag: Flag): CLIParserState = {
      Quirks.discard(rawArg)
      new GlobalParameters(Parameters(Vector(flag), Vector.empty))
    }

    override def addParameter(rawArg: String)(parameter: Value): CLIParserState = {
      Quirks.discard(rawArg)
      new GlobalParameters(Parameters(Vector.empty, Vector(parameter)))
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      new Error(ParserError.DanglingArgument(processed, arg))
    }

    override def splitter(processed: Vector[String])(): CLIParserState = {
      new Error(ParserError.DanglingSplitter(processed))
    }

    override def freeze(): Either[ParserError, RoleAppArguments] = {
      Right(RoleAppArguments(Parameters.empty, Vector.empty))
    }
  }

  class GlobalParameters(p: Parameters) extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      new RoleParameters(p, Vector.empty, RoleArg(name, Parameters.empty, Vector.empty))
    }

    override def addFlag(rawArg: String)(flag: Flag): CLIParserState = {
      new GlobalParameters(p.copy(flags = p.flags :+ flag))
    }

    override def addParameter(rawArg: String)(parameter: Value): CLIParserState = {
      new GlobalParameters(p.copy(values = p.values :+ parameter))
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      new Error(ParserError.DanglingArgument(processed, arg))
    }

    override def splitter(processed: Vector[String])(): CLIParserState = {
      new Error(ParserError.DanglingSplitter(processed))
    }

    override def freeze(): Either[ParserError, RoleAppArguments] = {
      Right(RoleAppArguments(p, Vector.empty))
    }
  }

  class RoleParameters(globalParameters: Parameters, roles: Vector[RoleArg], currentRole: RoleArg) extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      new RoleParameters(globalParameters, roles :+ currentRole, RoleArg(name, Parameters.empty, Vector.empty))
    }

    override def addFlag(rawArg: String)(flag: Flag): CLIParserState = {
      Quirks.discard(rawArg)
      new RoleParameters(globalParameters, roles, currentRole.copy(roleParameters = currentRole.roleParameters.copy(flags = currentRole.roleParameters.flags :+ flag)))
    }

    override def addParameter(rawArg: String)(parameter: Value): CLIParserState = {
      Quirks.discard(rawArg)
      new RoleParameters(globalParameters, roles, currentRole.copy(roleParameters = currentRole.roleParameters.copy(values = currentRole.roleParameters.values :+ parameter)))
    }

    override def splitter(processed: Vector[String])(): CLIParserState = {
      new RoleArgs(globalParameters, roles, currentRole)
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      new RoleArgs(globalParameters, roles, currentRole)
    }

    override def freeze(): Either[ParserError, RoleAppArguments] = {
      Right(RoleAppArguments(globalParameters, roles :+ currentRole))
    }
  }

  class RoleArgs(globalParameters: Parameters, roles: Vector[RoleArg], currentRole: RoleArg) extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      new RoleParameters(globalParameters, roles :+ currentRole, RoleArg(name, Parameters.empty, Vector.empty))
    }

    override def addFlag(rawArg: String)(flag: Flag): CLIParserState = {
      Quirks.discard(flag)
      new RoleArgs(globalParameters, roles, currentRole.copy(freeArgs = currentRole.freeArgs :+ rawArg))
    }

    override def addParameter(rawArg: String)(parameter: Value): CLIParserState = {
      Quirks.discard(parameter)
      new RoleArgs(globalParameters, roles, currentRole.copy(freeArgs = currentRole.freeArgs :+ rawArg))
    }

    override def splitter(processed: Vector[String])(): CLIParserState = {
      Quirks.discard(processed)
      new RoleArgs(globalParameters, roles, currentRole.copy(freeArgs = currentRole.freeArgs :+ "--"))
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      Quirks.discard(processed)
      new RoleArgs(globalParameters, roles, currentRole.copy(freeArgs = currentRole.freeArgs :+ arg))
    }

    override def freeze(): Either[ParserError, RoleAppArguments] = {
      Right(RoleAppArguments(globalParameters, roles :+ currentRole))
    }
  }

}


