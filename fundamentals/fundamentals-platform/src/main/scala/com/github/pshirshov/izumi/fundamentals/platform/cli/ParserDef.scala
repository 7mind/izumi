package com.github.pshirshov.izumi.fundamentals.platform.cli

import com.github.pshirshov.izumi.fundamentals.platform.cli.CLIParser.{ArgDef, ArgNameDef}

import scala.collection.mutable
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

trait ParserDef {
  private val _all = mutable.LinkedHashMap[String, ArgDef]()

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


  case class ParserRoleDescriptor(id: String, parser: ParserDef, doc: Option[String])

  case class ParserSchema(descriptors: Seq[ParserRoleDescriptor])

  /** TODOs:
    * - default parameters
    * - free arg restrictions
    * - decoding MUST fail on unknown parameters
    * - automated decoder: ParserSchema[CaseClass](args: RoleAppArguments): CaseClass (compile-time in ideal case)
    */
  def makeDocs(schema: ParserSchema): String = schema.descriptors.map(formatRoleHelp)
    .mkString("\n\n")


  def formatOptions(parser: ParserDef): Option[String] = {
    if (parser._all.nonEmpty) {
      Some(parser._all.values.map(formatArg).mkString("\n\n"))
    } else {
      None
    }
  }

  private[this] def formatRoleHelp(rb: ParserRoleDescriptor): String = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    val sub = Seq(rb.doc.toSeq, ParserDef.formatOptions(rb.parser).toSeq).flatten.mkString("\n\n")

    val id = s":${rb.id}"

    if (sub.nonEmpty) {
      Seq(id, sub.shift(2)).mkString("\n\n")
    } else {
      id
    }
  }

  private[this] def formatArg(arg: CLIParser.ArgDef): String = {
    val usage = (arg.name.short.map(_ => formatInfo(short = true, arg)).toSeq ++ Seq(formatInfo(short = false, arg))).mkString(", ")

    s"$usage\n    ${arg.doc}"
  }

  private[this] def formatInfo(short: Boolean, arg: CLIParser.ArgDef): String = {
    val name = if (short) {
      arg.name.short.get
    } else {
      arg.name.long
    }

    val prefix = if (!short && arg.valueDoc.isDefined) {
      "--"
    } else {
      "-"
    }

    arg.valueDoc match {
      case Some(value) =>
        if (short) {
          s"$prefix$name $value"
        } else {
          s"$prefix$name=$value"
        }
      case None =>
        s"$prefix$name"
    }

  }

}
