package com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw

import java.io.File
import java.nio.file.Path

case class RawAppArgs(globalParameters: RawEntrypointParams, roles: Vector[RawRoleParams])

case class RawRoleParams(role: String, roleParameters: RawEntrypointParams, freeArgs: Vector[String])

case class RawEntrypointParams(flags: Vector[RawFlag], values: Vector[RawValue])

object RawEntrypointParams {
  def empty: RawEntrypointParams = RawEntrypointParams(Vector.empty, Vector.empty)

}

case class RawFlag(name: String)

case class RawValue(name: String, value: String)

object RawValue {
  implicit class ValueExt(val value: RawValue) extends AnyVal {
    def asFile: File = new File(value.value)

    def asPath: Path = asFile.toPath

    def asString: String = value.value
  }

  implicit class MaybeValueExt(val value: Option[RawValue]) extends AnyVal {
    def asFile: Option[File] = value.map(_.asFile)

    def asPath: Option[Path] = asFile.map(_.toPath)

    def asString: Option[String] = value.map(_.value)
  }
}
