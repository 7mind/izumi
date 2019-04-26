package com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema

import com.github.pshirshov.izumi.fundamentals.platform.cli.model.schema.ParserDef.ArgDef
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

object ParserSchemaFormatter {
  private val withColors = !java.awt.GraphicsEnvironment.isHeadless


  def makeDocs(schema: ParserSchema): String = {
    val sb = new StringBuilder()

    val maindoc = formatParser(schema.globalArgsSchema.parserDef, schema.globalArgsSchema.doc, schema.globalArgsSchema.notes, 2, 0)
    if (maindoc.nonEmpty) {
      sb.append(maindoc)
      sb.append("\n\n")
    }

    sb.append(schema.descriptors.map(formatRoleHelp).mkString("\n"))
    sb.toString()
  }


  def formatOptions(parser: ParserDef): Option[String] = {
    if (!parser.isEmpty) {
      Some(parser.enumerate.map(formatArg).mkString("\n\n"))
    } else {
      None
    }
  }

  private[this] def formatRoleHelp(rb: RoleParserSchema): String = {
    val sb = new StringBuilder()
    if (withColors) {
      sb.append(Console.GREEN)
      sb.append(Console.BOLD)
    }
    sb.append(s":${rb.id}")
    if (withColors) {
      sb.append(Console.RESET)
    }

    if (rb.parser.nonEmpty) {
      if (withColors) {
        sb.append(Console.YELLOW)
      }
      sb.append(" [options]")
      if (withColors) {
        sb.append(Console.RESET)
      }
    }

    if (rb.freeArgsAllowed) {
      sb.append(" <args>")
    }
    sb.append("\n")

    sb.append(formatParser(rb.parser, rb.doc, rb.notes, 2, 2))

    sb.toString()
  }

  private def formatParser(parser: ParserDef, doc: Option[String], notes: Option[String], shift: Int, docShift: Int): String = {
    val sb = new StringBuilder()

    doc.foreach {
      doc =>
        sb.append("\n")
        sb.append(doc.shift(docShift))
        sb.append("\n")
    }

    if (parser.nonEmpty) {
      val opts = formatOptions(parser).toSeq.mkString("\n\n")
      sb.append("\n")
      sb.append(
        s"""Options:
           |
           |${opts.shift(2)}""".stripMargin.shift(shift))
    }

    notes.foreach {
      doc =>
        sb.append("\n")
        sb.append(doc.shift(docShift))
        sb.append("\n")
    }


    sb.toString()
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

    val basename = if (withColors) {
      s"${Console.YELLOW}$prefix$name${Console.RESET}"
    } else {
      s"$prefix$name"
    }

    arg.valueDoc match {
      case Some(value) =>
        if (short) {
          s"$basename $value"
        } else {
          s"$basename=$value"
        }
      case None =>
        basename
    }

  }

}
