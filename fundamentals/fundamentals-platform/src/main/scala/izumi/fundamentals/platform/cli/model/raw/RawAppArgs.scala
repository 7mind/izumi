package izumi.fundamentals.platform.cli.model.raw

import java.io.File
import java.nio.file.Path

import izumi.fundamentals.platform.cli.model.schema.ParserDef.ArgDef

final case class RawAppArgs(
  globalParameters: RawEntrypointParams,
  roles: Vector[RawRoleParams],
)

object RawAppArgs {
  def empty: RawAppArgs = RawAppArgs(RawEntrypointParams.empty, Vector.empty)
}

final case class RawRoleParams(role: String, roleParameters: RawEntrypointParams, freeArgs: Vector[String])

object RawRoleParams {
  def apply(role: String): RawRoleParams = RawRoleParams(role, RawEntrypointParams.empty, Vector.empty)
}

final case class RawEntrypointParams(flags: Vector[RawFlag], values: Vector[RawValue]) {
  def findValue(parameter: ArgDef): Option[RawValue] = values.find(parameter.name matches _.name)
  def findValues(parameter: ArgDef): Vector[RawValue] = values.filter(parameter.name matches _.name)
  def hasFlag(parameter: ArgDef): Boolean = flags.exists(parameter.name matches _.name)
  def hasNoFlag(parameter: ArgDef): Boolean = !hasFlag(parameter)
}

object RawEntrypointParams {
  def empty: RawEntrypointParams = RawEntrypointParams(Vector.empty, Vector.empty)
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
