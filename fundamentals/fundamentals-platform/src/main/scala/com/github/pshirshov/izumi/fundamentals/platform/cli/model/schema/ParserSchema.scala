package com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema

import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.{RawEntrypointParams, RawValue}
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema.ParserDef.ParserRoleDescriptor

case class ParserSchema(descriptors: Seq[ParserRoleDescriptor])

case class ArgDef private[cli](name: ArgNameDef, doc: String, valueDoc: Option[String])

object ArgDef {

  implicit class ParameterDefExt(val parameter: ArgDef) extends AnyVal {
    def findValue(parameters: RawEntrypointParams): Option[RawValue] = {
      parameters.values.find(p => parameter.name.matches(p.name))
    }

    def findValues(parameters: RawEntrypointParams): Vector[RawValue] = {
      parameters.values.filter(p => parameter.name.matches(p.name))
    }

    def hasFlag(parameters: RawEntrypointParams): Boolean = {
      parameters.flags.exists(p => parameter.name.matches(p.name))
    }

    def hasNoFlag(parameters: RawEntrypointParams): Boolean = {
      !hasFlag(parameters)
    }
  }

}

case class ArgNameDef private[cli](long: String, short: Option[String]) {
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
