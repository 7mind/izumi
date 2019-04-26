package com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.{RawEntrypointParams, RawValue}
import ParserDef._

import scala.collection.mutable

trait ParserDef {
  protected[schema] val _all: mutable.LinkedHashMap[String, ArgDef] = mutable.LinkedHashMap[String, ArgDef]()

  def all: Map[String, ArgDef] = _all.toMap

  def arg(name: String, short: String, doc: String, valdoc: String): ArgDef = {
    arg(name, Some(short), doc, Some(valdoc))
  }

  def arg(name: String, doc: String, valdoc: String): ArgDef = {
    arg(name, None, doc, Some(valdoc))
  }

  def flag(name: String, short: String, doc: String): ArgDef = {
    arg(name, Some(short), doc, None)
  }

  def flag(name: String, doc: String): ArgDef = {
    arg(name, None, doc, None)
  }

  private def arg(name: String, short: Option[String], doc: String, valueDoc: Option[String]): ArgDef = {
    if (_all.contains(name)) {
      throw new IllegalArgumentException(s"Parameter $name/$short is already registered!")
    }
    val argDef = ArgDef(ArgNameDef(name, short), doc, valueDoc)
    _all.put(name, argDef).discard()
    argDef
  }
}


object ParserDef {

  object Empty extends ParserDef

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

}
