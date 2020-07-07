package izumi.fundamentals.platform.cli.impl

import izumi.fundamentals.platform.cli.CLIParser.ParserError
import izumi.fundamentals.platform.cli.model.raw._
import izumi.fundamentals.platform.language.Quirks

sealed trait CLIParserState {
  def addRole(name: String): CLIParserState

  def addFlag(rawArg: String)(flag: RawFlag): CLIParserState

  def addParameter(rawArg: String)(parameter: RawValue): CLIParserState

  def openParameter(rawArg: String)(name: String): CLIParserState

  def addFreeArg(processed: Vector[String])(arg: String): CLIParserState

  def splitter(processed: Vector[String]): CLIParserState

  def freeze(): Either[ParserError, RawAppArgs]
}

object CLIParserState {

  class Error(error: ParserError) extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      Quirks.discard(name)
      this
    }

    override def addFlag(rawArg: String)(flag: RawFlag): CLIParserState = {
      Quirks.discard(rawArg, flag)
      this
    }

    override def addParameter(rawArg: String)(parameter: RawValue): CLIParserState = {
      Quirks.discard(rawArg, parameter)
      this
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      Quirks.discard(arg, processed)
      this
    }

    override def openParameter(rawArg: String)(name: String): CLIParserState = {
      Quirks.discard(rawArg, name)
      this
    }

    override def splitter(processed: Vector[String]): CLIParserState = {
      Quirks.discard(processed)
      this
    }

    override def freeze(): Either[ParserError, RawAppArgs] = {
      Left(error)
    }
  }

  class Initial() extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      new RoleParameters(RawEntrypointParams.empty, Vector.empty, RawRoleParams(name, RawEntrypointParams.empty, Vector.empty))
    }

    override def addFlag(rawArg: String)(flag: RawFlag): CLIParserState = {
      Quirks.discard(rawArg)
      new GlobalParameters(RawEntrypointParams(Vector(flag), Vector.empty))
    }

    override def addParameter(rawArg: String)(parameter: RawValue): CLIParserState = {
      Quirks.discard(rawArg)
      new GlobalParameters(RawEntrypointParams(Vector.empty, Vector(parameter)))
    }

    override def openParameter(rawArg: String)(name: String): CLIParserState = {
      Quirks.discard(rawArg)
      new OpenGlobalParameters(RawEntrypointParams.empty, name)
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      new Error(ParserError.DanglingArgument(processed, arg))
    }

    override def splitter(processed: Vector[String]): CLIParserState = {
      new Error(ParserError.DanglingSplitter(processed))
    }

    override def freeze(): Either[ParserError, RawAppArgs] = {
      Right(RawAppArgs(RawEntrypointParams.empty, Vector.empty))
    }
  }

  class OpenGlobalParameters(p: RawEntrypointParams, name: String) extends CLIParserState {

    override def openParameter(rawArg: String)(name: String): CLIParserState = {
      new OpenGlobalParameters(withFlag(), name)
    }

    override def addRole(name: String): CLIParserState = {
      new RoleParameters(withFlag(), Vector.empty, RawRoleParams(name, RawEntrypointParams.empty, Vector.empty))
    }

    override def addFlag(rawArg: String)(flag: RawFlag): CLIParserState = {
      new GlobalParameters(p.copy(flags = p.flags ++ Vector(RawFlag(name), flag)))
    }

    override def addParameter(rawArg: String)(parameter: RawValue): CLIParserState = {
      new GlobalParameters(withFlag().copy(values = p.values :+ parameter))
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      Quirks.discard(processed)
      new GlobalParameters(p.copy(values = p.values :+ RawValue(name, arg)))
    }

    override def splitter(processed: Vector[String]): CLIParserState = {
      new Error(ParserError.DanglingSplitter(processed))
    }

    override def freeze(): Either[ParserError, RawAppArgs] = {
      Right(RawAppArgs(withFlag(), Vector.empty))
    }

    private def withFlag(): RawEntrypointParams = {
      p.copy(flags = p.flags :+ RawFlag(this.name))
    }
  }

  class GlobalParameters(p: RawEntrypointParams) extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      new RoleParameters(p, Vector.empty, RawRoleParams(name, RawEntrypointParams.empty, Vector.empty))
    }

    override def addFlag(rawArg: String)(flag: RawFlag): CLIParserState = {
      new GlobalParameters(p.copy(flags = p.flags :+ flag))
    }

    override def openParameter(rawArg: String)(name: String): CLIParserState = {
      Quirks.discard(rawArg)
      new OpenGlobalParameters(p, name)
    }

    override def addParameter(rawArg: String)(parameter: RawValue): CLIParserState = {
      new GlobalParameters(p.copy(values = p.values :+ parameter))
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      new Error(ParserError.DanglingArgument(processed, arg))
    }

    override def splitter(processed: Vector[String]): CLIParserState = {
      new Error(ParserError.DanglingSplitter(processed))
    }

    override def freeze(): Either[ParserError, RawAppArgs] = {
      Right(RawAppArgs(p, Vector.empty))
    }
  }

  class RoleParameters(globalParameters: RawEntrypointParams, roles: Vector[RawRoleParams], currentRole: RawRoleParams) extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      new RoleParameters(globalParameters, roles :+ currentRole, RawRoleParams(name, RawEntrypointParams.empty, Vector.empty))
    }

    override def openParameter(rawArg: String)(name: String): CLIParserState = {
      new OpenRoleParameters(globalParameters, roles, currentRole, name)
    }

    override def addFlag(rawArg: String)(flag: RawFlag): CLIParserState = {
      Quirks.discard(rawArg)
      new RoleParameters(globalParameters, roles, currentRole.copy(roleParameters = currentRole.roleParameters.copy(flags = currentRole.roleParameters.flags :+ flag)))
    }

    override def addParameter(rawArg: String)(parameter: RawValue): CLIParserState = {
      Quirks.discard(rawArg)
      new RoleParameters(
        globalParameters,
        roles,
        currentRole.copy(roleParameters = currentRole.roleParameters.copy(values = currentRole.roleParameters.values :+ parameter)),
      )
    }

    override def splitter(processed: Vector[String]): CLIParserState = {
      new RoleArgs(globalParameters, roles, currentRole)
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      new RoleArgs(globalParameters, roles, currentRole.copy(freeArgs = currentRole.freeArgs :+ arg))
    }

    override def freeze(): Either[ParserError, RawAppArgs] = {
      Right(RawAppArgs(globalParameters, roles :+ currentRole))
    }
  }

  class OpenRoleParameters(globalParameters: RawEntrypointParams, roles: Vector[RawRoleParams], currentRole: RawRoleParams, name: String) extends CLIParserState {

    override def openParameter(rawArg: String)(name: String): CLIParserState = {
      new OpenRoleParameters(globalParameters, roles, withFlag(), name)
    }

    override def addRole(name: String): CLIParserState = {
      new RoleParameters(globalParameters, roles :+ withFlag(), RawRoleParams(name, RawEntrypointParams.empty, Vector.empty))
    }

    override def addFlag(rawArg: String)(flag: RawFlag): CLIParserState = {
      new RoleParameters(
        globalParameters,
        roles,
        currentRole.copy(roleParameters = currentRole.roleParameters.copy(flags = currentRole.roleParameters.flags ++ Vector(RawFlag(name), flag))),
      )
    }

    override def addParameter(rawArg: String)(parameter: RawValue): CLIParserState = {
      val wf = withFlag()
      new RoleParameters(globalParameters, roles, wf.copy(roleParameters = wf.roleParameters.copy(values = wf.roleParameters.values :+ parameter)))
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      Quirks.discard(processed)
      new RoleParameters(
        globalParameters,
        roles,
        currentRole.copy(roleParameters = currentRole.roleParameters.copy(values = currentRole.roleParameters.values :+ RawValue(name, arg))),
      )

    }

    override def splitter(processed: Vector[String]): CLIParserState = {
      new Error(ParserError.DanglingSplitter(processed))
    }

    override def freeze(): Either[ParserError, RawAppArgs] = {
      Right(RawAppArgs(globalParameters, roles :+ withFlag()))
    }

    private def withFlag(): RawRoleParams = {
      currentRole.copy(roleParameters = currentRole.roleParameters.copy(flags = currentRole.roleParameters.flags :+ RawFlag(this.name)))
    }
  }

  class RoleArgs(globalParameters: RawEntrypointParams, roles: Vector[RawRoleParams], currentRole: RawRoleParams) extends CLIParserState {
    override def addRole(name: String): CLIParserState = {
      new RoleParameters(globalParameters, roles :+ currentRole, RawRoleParams(name, RawEntrypointParams.empty, Vector.empty))
    }

    override def openParameter(rawArg: String)(name: String): CLIParserState = {
      Quirks.discard(name)
      extend(rawArg)
    }

    override def addFlag(rawArg: String)(flag: RawFlag): CLIParserState = {
      Quirks.discard(flag)
      extend(rawArg)
    }

    override def addParameter(rawArg: String)(parameter: RawValue): CLIParserState = {
      Quirks.discard(parameter)
      extend(rawArg)
    }

    override def splitter(processed: Vector[String]): CLIParserState = {
      Quirks.discard(processed)
      extend("--")
    }

    override def addFreeArg(processed: Vector[String])(arg: String): CLIParserState = {
      Quirks.discard(processed)
      extend(arg)
    }

    override def freeze(): Either[ParserError, RawAppArgs] = {
      Right(RawAppArgs(globalParameters, roles :+ currentRole))
    }

    private def extend(rawArg: String): CLIParserState = {
      new RoleArgs(globalParameters, roles, currentRole.copy(freeArgs = currentRole.freeArgs :+ rawArg))
    }
  }

}
