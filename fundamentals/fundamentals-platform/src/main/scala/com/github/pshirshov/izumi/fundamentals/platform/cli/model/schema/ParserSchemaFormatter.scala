package com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema

import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema.ParserDef.ArgDef

object ParserSchemaFormatter {
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

  private[this] def formatRoleHelp(rb: RoleParserSchema): String = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    val sub = Seq(rb.doc.toSeq, formatOptions(rb.parser).toSeq).flatten.mkString("\n\n")

    val id = s":${rb.id}"

    if (sub.nonEmpty) {
      Seq(id, sub.shift(2)).mkString("\n\n")
    } else {
      id
    }
  }

  private[this] def formatArg(arg: ArgDef): String = {
    val usage = (arg.name.short.map(_ => formatInfo(short = true, arg)).toSeq ++ Seq(formatInfo(short = false, arg))).mkString(", ")

    s"$usage\n    ${arg.doc}"
  }

  private[this] def formatInfo(short: Boolean, arg: ArgDef): String = {
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
