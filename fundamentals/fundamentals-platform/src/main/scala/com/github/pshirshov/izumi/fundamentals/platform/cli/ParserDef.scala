package com.github.pshirshov.izumi.fundamentals.platform.cli

import com.github.pshirshov.izumi.fundamentals.platform.cli.CLIParser.{ArgDef, ArgNameDef}

import scala.collection.mutable
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

trait ParserDef {
  private val _all = mutable.HashMap[String, ArgDef]()

  def all: Map[String, ArgDef] = _all.toMap

  def arg(name: String, short: String, doc: String): ArgDef = {
    arg(name, Some(short), doc)
  }

  def arg(name: String, doc: String): ArgDef = {
    arg(name, None, doc)
  }


  private def arg(name: String, short: Option[String], doc: String): ArgDef = {
    if (_all.contains(name)) {
      throw new IllegalArgumentException(s"Parameter $name/$short is already registered!")
    }
    val argDef = ArgDef(ArgNameDef(name, short), doc)
    _all.put(name, argDef).discard()
    argDef
  }
}

object ParserDef {

  object Empty extends ParserDef

}
