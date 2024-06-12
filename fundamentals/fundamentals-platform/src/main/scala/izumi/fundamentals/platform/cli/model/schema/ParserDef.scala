package izumi.fundamentals.platform.cli.model.schema

import izumi.fundamentals.platform.cli.model.raw.{RawEntrypointParams, RawValue}
import izumi.fundamentals.platform.cli.model.schema.ParserDef._

import scala.collection.mutable

/**
  * Example:
  *
  * {{{
  * object Options extends ParserDef {
  *   final val logLevelRootParam = arg("log-level-root", "ll", "root log level", "{trace|debug|info|warn|error|critical}")
  *   final val logFormatParam = arg("log-format", "lf", "log format", "{text|json}")
  *   final val configParam = arg("config", "c", "path to config file", "<path>")
  *   final val dumpContext = flag("debug-dump-graph", "dump DI graph for debugging")
  *   final val use = arg("use", "u", "activate a choice on functionality axis", "<axis>:<choice>")
  * }
  * }}}
  */
trait ParserDef {
  private val _all: mutable.LinkedHashMap[String, ArgDef] = mutable.LinkedHashMap[String, ArgDef]()

  def isEmpty: Boolean = _all.isEmpty
  def nonEmpty: Boolean = _all.nonEmpty

  def enumerate: Seq[ArgDef] = _all.toSeq.map(_._2)
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
    _all.put(name, argDef)
    argDef
  }
}

object ParserDef {

  object Empty extends ParserDef

  final case class ArgDef private[cli] (name: ArgNameDef, doc: String, valueDoc: Option[String])

  object ArgDef {
    implicit final class ParameterDefExt(val parameter: ArgDef) extends AnyVal {
      def findValue(parameters: RawEntrypointParams): Option[RawValue] = parameters.findValue(parameter)
      def findValues(parameters: RawEntrypointParams): Vector[RawValue] = parameters.findValues(parameter)
      def hasFlag(parameters: RawEntrypointParams): Boolean = parameters.hasFlag(parameter)
      def hasNoFlag(parameters: RawEntrypointParams): Boolean = parameters.hasNoFlag(parameter)
    }
  }

  final case class ArgNameDef private[cli] (long: String, short: Option[String]) {
    def all: Set[String] = Set(long) ++ short.toSet

    infix def matches(name: String): Boolean = all.contains(name)

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
