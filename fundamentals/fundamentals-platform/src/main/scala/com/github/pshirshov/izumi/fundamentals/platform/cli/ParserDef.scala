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

  def formatOptions(parser: ParserDef): Option[String] = {
    if (parser._all.nonEmpty) {
      Some(parser._all.values.map(formatArg).mkString("\n\n"))
    } else {
      None
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
