package izumi.fundamentals.platform.cli.model

import izumi.fundamentals.platform.cli.model.schema.ParserDef.ArgDef

import java.io.File
import java.nio.file.Path

final case class RoleAppArgs(
  globalParameters: EntrypointArgs,
  roles: Vector[RoleArgs],
)
object RoleAppArgs {
  def empty: RoleAppArgs = RoleAppArgs(EntrypointArgs.empty, Vector.empty)
}

final case class RoleArgs(
  role: String,
  roleParameters: EntrypointArgs,
)

object RoleArgs {
  def apply(role: String): RoleArgs = RoleArgs(role, EntrypointArgs.empty)
}

final case class RequiredRoles(requiredRoles: Vector[RoleArgs])

final case class EntrypointArgs(
  raw: Vector[String],
  flags: Vector[RawFlag],
  values: Vector[RawValue],
  freeArgs: Vector[String],
) {
  def findValue(parameter: ArgDef): Option[RawValue] = values.find(parameter.name matches _.name)
  def findValues(parameter: ArgDef): Vector[RawValue] = values.filter(parameter.name matches _.name)
  def hasFlag(parameter: ArgDef): Boolean = flags.exists(parameter.name matches _.name)
  def hasNoFlag(parameter: ArgDef): Boolean = !hasFlag(parameter)

  def findValue(parameter: Option[ArgDef]): Option[RawValue] = parameter.flatMap(findValue)
  def findValues(parameter: Option[ArgDef]): Vector[RawValue] = parameter.toVector.flatMap(findValues)
  def hasFlag(parameter: Option[ArgDef]): Boolean = parameter.fold(false)(hasFlag)
  def hasNoFlag(parameter: Option[ArgDef]): Boolean = parameter.fold(true)(hasNoFlag)
}
object EntrypointArgs {
  def empty: EntrypointArgs = EntrypointArgs(Vector.empty, Vector.empty, Vector.empty, Vector.empty)
}

final case class RawFlag(name: String)

final case class RawValue(name: String, value: String)
object RawValue {
  implicit final class ValueExt(val value: RawValue) extends AnyVal {
    def asFile: File = new File(value.value)
    def asPath: Path = asFile.toPath
    def asString: String = value.value
  }

  implicit final class MaybeValueExt(val value: Option[RawValue]) extends AnyVal {
    def asFile: Option[File] = value.map(_.asFile)
    def asPath: Option[Path] = asFile.map(_.toPath)
    def asString: Option[String] = value.map(_.value)
  }
}
